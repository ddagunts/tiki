package com.tkey.session

import android.util.Log
import com.google.protobuf.ByteString
import com.tesla.generated.carserver.common.Common
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.carserver.vehicle.Vehicle
import com.tesla.generated.keys.Keys
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.vcsec.Vcsec
import com.tkey.ble.CarConnection
import com.tkey.crypto.Identity
import com.tkey.crypto.Metadata
import com.tkey.crypto.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom

private const val TAG = "TKey.Sess"

/**
 * Drives the encrypted session handshake on top of [CarConnection].
 *
 *   1. Listens to [CarConnection.incoming] and parses each frame as
 *      [UniversalMessage.RoutableMessage].
 *   2. On request, sends a `session_info_request` to a given [Domain] carrying our
 *      P-256 public key and a random 16-byte challenge.
 *   3. When the vehicle replies with a `session_info` (Signatures.SessionInfo), derives
 *      an AES-GCM/HMAC [Session] via [Identity.deriveSession] and stores it per-domain
 *      (VCSEC and Infotainment have independent sessions).
 */
class TeslaSession(
    private val identity: Identity,
    private val connection: CarConnection,
    val vin: String,
) {

    /** State cached after a successful session_info handshake with a specific domain. */
    private data class DomainState(
        val session: Session,
        val epoch: ByteArray,
        var counter: Int,
        /** Vehicle clock at the moment we observed the SessionInfo. */
        val clockAtHandshake: Long,
        /** System.currentTimeMillis() at the moment we observed the SessionInfo. */
        val realTimeAtHandshake: Long,
    )

    private val domainStates = mutableMapOf<UniversalMessage.Domain, DomainState>()

    sealed class Status {
        data object Idle : Status()
        data class Requested(val domain: UniversalMessage.Domain) : Status()
        data class Established(
            val domain: UniversalMessage.Domain,
            val counter: Int,
            val epochHex: String,
            val vehicleClockSec: Long,
            val statusEnum: Signatures.Session_Info_Status,
        ) : Status()
        data class Failed(val reason: String) : Status()
    }

    private val scope = CoroutineScope(SupervisorJob())
    private val rng = SecureRandom()
    private val sessions = mutableMapOf<UniversalMessage.Domain, Session>()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    sealed class Enrollment {
        data object Idle : Enrollment()
        data object Requested : Enrollment()
        /** VCSEC accepted the request and is waiting for the user to tap the keycard. */
        data object AwaitingKeycard : Enrollment()
        data object Success : Enrollment()
        data class Failed(val reason: String) : Enrollment()
    }
    private val _enrollment = MutableStateFlow<Enrollment>(Enrollment.Idle)
    val enrollment: StateFlow<Enrollment> = _enrollment.asStateFlow()

    /** Latest VehicleStatus pushed by VCSEC (either auto-broadcast or in response to GET_STATUS). */
    private val _vehicleStatus = MutableStateFlow<VehicleStatusSnapshot?>(null)
    val vehicleStatus: StateFlow<VehicleStatusSnapshot?> = _vehicleStatus.asStateFlow()

    /** Latest VehicleData decoded from an Infotainment Response. */
    private val _vehicleData = MutableStateFlow<VehicleDataSnapshot?>(null)
    val vehicleData: StateFlow<VehicleDataSnapshot?> = _vehicleData.asStateFlow()

    /**
     * Outstanding Infotainment requests indexed by the UUID we generated. Each entry
     * stores the AES-GCM authentication tag we used on the request — needed to compute
     * the AAD for the response decryption.
     */
    private data class PendingRequest(val tag: ByteArray, val label: String, val sentAtMs: Long)
    private val pendingByUuid = mutableMapOf<ByteString, PendingRequest>()

    /** Number of inbound RoutableMessages parsed since [start]. Exposed for the UI. */
    private val _rxCount = MutableStateFlow(0)
    val rxCount: StateFlow<Int> = _rxCount.asStateFlow()

    fun start() {
        scope.launch {
            connection.incoming.collect { bytes -> handleIncoming(bytes) }
        }
    }

    fun stop() {
        scope.cancel()
    }

    /** Look up the derived session for a domain. Null until handshake completes. */
    fun sessionFor(domain: UniversalMessage.Domain): Session? = sessions[domain]

    /** True after we've done a successful session_info handshake for [domain]. */
    fun isReady(domain: UniversalMessage.Domain): Boolean = domainStates.containsKey(domain)

    suspend fun lock() = sendVcsecSigned(
        Vcsec.UnsignedMessage.newBuilder().setRKEAction(Vcsec.RKEAction_E.RKE_ACTION_LOCK).build(),
        label = "RKE_LOCK",
    )
    suspend fun unlock() = sendVcsecSigned(
        Vcsec.UnsignedMessage.newBuilder().setRKEAction(Vcsec.RKEAction_E.RKE_ACTION_UNLOCK).build(),
        label = "RKE_UNLOCK",
    )

    /**
     * Wake the car's infotainment domain so it can answer session_info / vehicle_data.
     * VCSEC stays up while the car sleeps, but infotainment doesn't — without this, our
     * session_info_request to DOMAIN_INFOTAINMENT silently goes nowhere.
     */
    suspend fun wakeVehicle() = sendVcsecSigned(
        Vcsec.UnsignedMessage.newBuilder().setRKEAction(Vcsec.RKEAction_E.RKE_ACTION_WAKE_VEHICLE).build(),
        label = "RKE_WAKE",
    )

    suspend fun openFrunk() = sendClosure(label = "FRUNK_OPEN") { setFrontTrunk(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_OPEN) }
    suspend fun openTrunk() = sendClosure(label = "TRUNK_OPEN") { setRearTrunk(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_OPEN) }
    suspend fun closeTrunk() = sendClosure(label = "TRUNK_CLOSE") { setRearTrunk(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_CLOSE) }
    suspend fun openChargePort() = sendClosure(label = "CHARGE_PORT_OPEN") { setChargePort(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_OPEN) }
    suspend fun closeChargePort() = sendClosure(label = "CHARGE_PORT_CLOSE") { setChargePort(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_CLOSE) }

    private suspend fun sendClosure(label: String, build: Vcsec.ClosureMoveRequest.Builder.() -> Unit) {
        val req = Vcsec.ClosureMoveRequest.newBuilder().apply(build).build()
        val unsigned = Vcsec.UnsignedMessage.newBuilder().setClosureMoveRequest(req).build()
        sendVcsecSigned(unsigned, label = label)
    }

    // -- Infotainment domain ------------------------------------------------------------

    suspend fun ventWindows() = sendInfotainmentAction(
        label = "WINDOWS_VENT",
        build = {
            setVehicleControlWindowAction(
                CarServer.VehicleControlWindowAction.newBuilder().setVent(VOID)
            )
        },
    )

    suspend fun closeWindows() = sendInfotainmentAction(
        label = "WINDOWS_CLOSE",
        build = {
            setVehicleControlWindowAction(
                CarServer.VehicleControlWindowAction.newBuilder().setClose(VOID)
            )
        },
    )

    suspend fun setVolumeAbsolute(level: Float) = sendInfotainmentAction(
        label = "VOLUME_SET",
        build = {
            setMediaUpdateVolume(
                CarServer.MediaUpdateVolume.newBuilder()
                    .setVolumeAbsoluteFloat(level)
            )
        },
    )

    suspend fun bumpVolume(delta: Int) = sendInfotainmentAction(
        label = "VOLUME_BUMP",
        build = {
            setMediaUpdateVolume(
                CarServer.MediaUpdateVolume.newBuilder()
                    .setVolumeDelta(delta)
            )
        },
    )

    suspend fun mediaNextTrack() = sendInfotainmentAction(
        label = "MEDIA_NEXT_TRACK",
        build = { setMediaNextTrack(CarServer.MediaNextTrack.getDefaultInstance()) },
    )

    suspend fun mediaPreviousTrack() = sendInfotainmentAction(
        label = "MEDIA_PREV_TRACK",
        build = { setMediaPreviousTrack(CarServer.MediaPreviousTrack.getDefaultInstance()) },
    )

    suspend fun mediaTogglePlayback() = sendInfotainmentAction(
        label = "MEDIA_TOGGLE",
        build = { setMediaPlayAction(CarServer.MediaPlayAction.getDefaultInstance()) },
    )

    suspend fun mediaNextFavorite() = sendInfotainmentAction(
        label = "MEDIA_NEXT_FAV",
        build = { setMediaNextFavorite(CarServer.MediaNextFavorite.getDefaultInstance()) },
    )

    suspend fun mediaPreviousFavorite() = sendInfotainmentAction(
        label = "MEDIA_PREV_FAV",
        build = { setMediaPreviousFavorite(CarServer.MediaPreviousFavorite.getDefaultInstance()) },
    )

    // -- Climate / HVAC -----------------------------------------------------------------

    suspend fun climateOn() = sendInfotainmentAction(
        label = "CLIMATE_ON",
        build = {
            setHvacAutoAction(CarServer.HvacAutoAction.newBuilder().setPowerOn(true))
        },
    )

    suspend fun climateOff() = sendInfotainmentAction(
        label = "CLIMATE_OFF",
        build = {
            setHvacAutoAction(CarServer.HvacAutoAction.newBuilder().setPowerOn(false))
        },
    )

    /**
     * Match Tesla's vehicle-command Go reference: set both driver/passenger temps in °C and
     * mark `Level = TEMP_MAX` so the receiver interprets the absolute_celsius fields as the
     * target. See teslamotors/vehicle-command pkg/vehicle/climate.go::ChangeClimateTemp.
     */
    suspend fun setClimateTemperature(driverC: Float, passengerC: Float) = sendInfotainmentAction(
        label = "CLIMATE_TEMP",
        build = {
            setHvacTemperatureAdjustmentAction(
                CarServer.HvacTemperatureAdjustmentAction.newBuilder()
                    .setDriverTempCelsius(driverC)
                    .setPassengerTempCelsius(passengerC)
                    .setLevel(
                        CarServer.HvacTemperatureAdjustmentAction.Temperature.newBuilder()
                            .setTEMPMAX(VOID)
                    )
            )
        },
    )

    suspend fun setSteeringWheelHeater(on: Boolean) = sendInfotainmentAction(
        label = "STW_HEATER",
        build = {
            setHvacSteeringWheelHeaterAction(
                CarServer.HvacSteeringWheelHeaterAction.newBuilder().setPowerOn(on)
            )
        },
    )

    suspend fun setBioweaponMode(on: Boolean, manualOverride: Boolean = true) = sendInfotainmentAction(
        label = "BIOWEAPON",
        build = {
            setHvacBioweaponModeAction(
                CarServer.HvacBioweaponModeAction.newBuilder()
                    .setOn(on)
                    .setManualOverride(manualOverride)
            )
        },
    )

    suspend fun setPreconditioningMax(on: Boolean, manualOverride: Boolean = true) = sendInfotainmentAction(
        label = "PRECONDITION_MAX",
        build = {
            setHvacSetPreconditioningMaxAction(
                CarServer.HvacSetPreconditioningMaxAction.newBuilder()
                    .setOn(on)
                    .setManualOverride(manualOverride)
            )
        },
    )

    suspend fun setClimateKeeperMode(
        mode: CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E,
        manualOverride: Boolean = true,
    ) = sendInfotainmentAction(
        label = "CLIMATE_KEEPER",
        build = {
            setHvacClimateKeeperAction(
                CarServer.HvacClimateKeeperAction.newBuilder()
                    .setClimateKeeperAction(mode)
                    .setManualOverride(manualOverride)
            )
        },
    )

    suspend fun setCabinOverheatProtection(on: Boolean, fanOnly: Boolean = false) = sendInfotainmentAction(
        label = "COP",
        build = {
            setSetCabinOverheatProtectionAction(
                CarServer.SetCabinOverheatProtectionAction.newBuilder()
                    .setOn(on)
                    .setFanOnly(fanOnly)
            )
        },
    )

    // -- Seats --------------------------------------------------------------------------

    enum class SeatPosition { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_CENTER, REAR_RIGHT }
    enum class HeaterLevel { OFF, LOW, MED, HIGH }
    enum class CoolerLevel { OFF, LOW, MED, HIGH }

    suspend fun setSeatHeater(seat: SeatPosition, level: HeaterLevel) = sendInfotainmentAction(
        label = "SEAT_HEATER",
        build = {
            val a = CarServer.HvacSeatHeaterActions.HvacSeatHeaterAction.newBuilder()
            when (level) {
                HeaterLevel.OFF -> a.setSEATHEATEROFF(VOID)
                HeaterLevel.LOW -> a.setSEATHEATERLOW(VOID)
                HeaterLevel.MED -> a.setSEATHEATERMED(VOID)
                HeaterLevel.HIGH -> a.setSEATHEATERHIGH(VOID)
            }
            when (seat) {
                SeatPosition.FRONT_LEFT -> a.setCARSEATFRONTLEFT(VOID)
                SeatPosition.FRONT_RIGHT -> a.setCARSEATFRONTRIGHT(VOID)
                SeatPosition.REAR_LEFT -> a.setCARSEATREARLEFT(VOID)
                SeatPosition.REAR_CENTER -> a.setCARSEATREARCENTER(VOID)
                SeatPosition.REAR_RIGHT -> a.setCARSEATREARRIGHT(VOID)
            }
            setHvacSeatHeaterActions(
                CarServer.HvacSeatHeaterActions.newBuilder()
                    .addHvacSeatHeaterAction(a)
            )
        },
    )

    /**
     * Front-seat cooling. vehicle-command's SetSeatCooler offsets the enum by +1 because
     * proto's `HvacSeatCoolerLevel_Off = 1`. We mirror the explicit enum here for clarity.
     */
    suspend fun setSeatCooler(seat: SeatPosition, level: CoolerLevel) = sendInfotainmentAction(
        label = "SEAT_COOLER",
        build = {
            val position = when (seat) {
                SeatPosition.FRONT_LEFT ->
                    CarServer.HvacSeatCoolerActions.HvacSeatCoolerPosition_E.HvacSeatCoolerPosition_FrontLeft
                SeatPosition.FRONT_RIGHT ->
                    CarServer.HvacSeatCoolerActions.HvacSeatCoolerPosition_E.HvacSeatCoolerPosition_FrontRight
                else -> error("Seat cooling only available on front seats")
            }
            val lvl = when (level) {
                CoolerLevel.OFF ->
                    CarServer.HvacSeatCoolerActions.HvacSeatCoolerLevel_E.HvacSeatCoolerLevel_Off
                CoolerLevel.LOW ->
                    CarServer.HvacSeatCoolerActions.HvacSeatCoolerLevel_E.HvacSeatCoolerLevel_Low
                CoolerLevel.MED ->
                    CarServer.HvacSeatCoolerActions.HvacSeatCoolerLevel_E.HvacSeatCoolerLevel_Med
                CoolerLevel.HIGH ->
                    CarServer.HvacSeatCoolerActions.HvacSeatCoolerLevel_E.HvacSeatCoolerLevel_High
            }
            setHvacSeatCoolerActions(
                CarServer.HvacSeatCoolerActions.newBuilder()
                    .addHvacSeatCoolerAction(
                        CarServer.HvacSeatCoolerActions.HvacSeatCoolerAction.newBuilder()
                            .setSeatPosition(position)
                            .setSeatCoolerLevel(lvl)
                    )
            )
        },
    )

    suspend fun setAutoSeatClimate(seat: SeatPosition, on: Boolean) = sendInfotainmentAction(
        label = "AUTO_SEAT",
        build = {
            val position = when (seat) {
                SeatPosition.FRONT_LEFT ->
                    CarServer.AutoSeatClimateAction.AutoSeatPosition_E.AutoSeatPosition_FrontLeft
                SeatPosition.FRONT_RIGHT ->
                    CarServer.AutoSeatClimateAction.AutoSeatPosition_E.AutoSeatPosition_FrontRight
                else -> error("Auto seat climate only available on front seats")
            }
            setAutoSeatClimateAction(
                CarServer.AutoSeatClimateAction.newBuilder()
                    .addCarseat(
                        CarServer.AutoSeatClimateAction.CarSeat.newBuilder()
                            .setSeatPosition(position)
                            .setOn(on)
                    )
            )
        },
    )

    // -- Charging -----------------------------------------------------------------------

    suspend fun chargeStart() = sendInfotainmentAction(
        label = "CHARGE_START",
        build = {
            setChargingStartStopAction(
                CarServer.ChargingStartStopAction.newBuilder().setStart(VOID)
            )
        },
    )

    suspend fun chargeStop() = sendInfotainmentAction(
        label = "CHARGE_STOP",
        build = {
            setChargingStartStopAction(
                CarServer.ChargingStartStopAction.newBuilder().setStop(VOID)
            )
        },
    )

    suspend fun setChargeLimit(percent: Int) = sendInfotainmentAction(
        label = "CHARGE_LIMIT",
        build = {
            setChargingSetLimitAction(
                CarServer.ChargingSetLimitAction.newBuilder().setPercent(percent)
            )
        },
    )

    suspend fun setChargingAmps(amps: Int) = sendInfotainmentAction(
        label = "CHARGE_AMPS",
        build = {
            setSetChargingAmpsAction(
                CarServer.SetChargingAmpsAction.newBuilder().setChargingAmps(amps)
            )
        },
    )

    // -- Misc vehicle controls ----------------------------------------------------------

    suspend fun flashLights() = sendInfotainmentAction(
        label = "FLASH",
        build = {
            setVehicleControlFlashLightsAction(
                CarServer.VehicleControlFlashLightsAction.getDefaultInstance()
            )
        },
    )

    suspend fun honkHorn() = sendInfotainmentAction(
        label = "HONK",
        build = {
            setVehicleControlHonkHornAction(
                CarServer.VehicleControlHonkHornAction.getDefaultInstance()
            )
        },
    )

    suspend fun setSentryMode(on: Boolean) = sendInfotainmentAction(
        label = "SENTRY",
        build = {
            setVehicleControlSetSentryModeAction(
                CarServer.VehicleControlSetSentryModeAction.newBuilder().setOn(on)
            )
        },
    )

    /**
     * Ask the car for its current [Vehicle.VehicleData]. Issued as several small requests
     * because asking for everything at once trips
     * `MESSAGEFAULT_ERROR_RESPONSE_MTU_EXCEEDED` — the encrypted VehicleData response
     * doesn't fit a single BLE-MTU-bounded frame. Closures intentionally omitted; VCSEC
     * auto-broadcasts those via VehicleStatus.
     *
     * Each response is merged into [_vehicleData] in [handleInfotainmentResponse] so the
     * UI sees fields populate as they arrive.
     */
    suspend fun requestVehicleData() {
        requestChargeState()
        requestClimateState()
        requestTirePressureState()
        requestMediaState()
    }

    suspend fun requestChargeState() = sendInfotainmentAction(
        label = "GET_CHARGE_STATE",
        build = {
            setGetVehicleData(
                CarServer.GetVehicleData.newBuilder()
                    .setGetChargeState(CarServer.GetChargeState.getDefaultInstance())
            )
        },
        encryptResponse = true,
    )

    suspend fun requestClimateState() = sendInfotainmentAction(
        label = "GET_CLIMATE_STATE",
        build = {
            setGetVehicleData(
                CarServer.GetVehicleData.newBuilder()
                    .setGetClimateState(CarServer.GetClimateState.getDefaultInstance())
            )
        },
        encryptResponse = true,
    )

    suspend fun requestTirePressureState() = sendInfotainmentAction(
        label = "GET_TIRE_STATE",
        build = {
            setGetVehicleData(
                CarServer.GetVehicleData.newBuilder()
                    .setGetTirePressureState(CarServer.GetTirePressureState.getDefaultInstance())
            )
        },
        encryptResponse = true,
    )

    suspend fun requestMediaState() = sendInfotainmentAction(
        label = "GET_MEDIA_STATE",
        build = {
            setGetVehicleData(
                CarServer.GetVehicleData.newBuilder()
                    .setGetMediaState(CarServer.GetMediaState.getDefaultInstance())
            )
        },
        encryptResponse = true,
    )

    private suspend fun sendInfotainmentAction(
        label: String,
        encryptResponse: Boolean = false,
        build: CarServer.VehicleAction.Builder.() -> Unit,
    ) {
        val vehicleAction = CarServer.VehicleAction.newBuilder().apply(build).build()
        val action = CarServer.Action.newBuilder()
            .setVehicleAction(vehicleAction)
            .build()
        sendInfotainmentSigned(action.toByteArray(), label, encryptResponse)
    }

    private suspend fun sendInfotainmentSigned(
        plaintext: ByteArray,
        label: String,
        encryptResponse: Boolean,
    ) {
        val state = domainStates[UniversalMessage.Domain.DOMAIN_INFOTAINMENT]
            ?: error("Infotainment session not yet established — call requestSessionInfo(DOMAIN_INFOTAINMENT) first")

        state.counter += 1
        val counter = state.counter
        val expiresAt = vehicleClockNow(state) + COMMAND_EXPIRY_SEC
        // Tesla's "Flags" enum gives bit *positions*: FLAG_ENCRYPT_RESPONSE=1 → bit 1 → mask 0b10.
        val flags = if (encryptResponse) 1 shl UniversalMessage.Flags.FLAG_ENCRYPT_RESPONSE_VALUE else 0

        val aadBuilder = Metadata.sha256()
            .addByte(
                Metadata.Tag.SIGNATURE_TYPE,
                Signatures.SignatureType.SIGNATURE_TYPE_AES_GCM_PERSONALIZED.number,
            )
            .addByte(Metadata.Tag.DOMAIN, UniversalMessage.Domain.DOMAIN_INFOTAINMENT.number)
            .add(Metadata.Tag.PERSONALIZATION, vin.toByteArray(Charsets.US_ASCII))
            .add(Metadata.Tag.EPOCH, state.epoch)
            .addUint32BE(Metadata.Tag.EXPIRES_AT, expiresAt)
            .addUint32BE(Metadata.Tag.COUNTER, counter)
        if (flags != 0) aadBuilder.addUint32BE(Metadata.Tag.FLAGS, flags)
        val aad = aadBuilder.checksum()

        val frame = state.session.encrypt(plaintext, aad)

        val sigData = Signatures.SignatureData.newBuilder()
            .setSignerIdentity(
                Signatures.KeyIdentity.newBuilder()
                    .setPublicKey(ByteString.copyFrom(identity.publicKeyBytes()))
            )
            .setAESGCMPersonalizedData(
                Signatures.AES_GCM_Personalized_Signature_Data.newBuilder()
                    .setEpoch(ByteString.copyFrom(state.epoch))
                    .setNonce(ByteString.copyFrom(frame.nonce))
                    .setCounter(counter)
                    .setExpiresAt(expiresAt)
                    .setTag(ByteString.copyFrom(frame.tag))
            )
            .build()

        val uuidBytes = randomBytes(16)
        val uuid = ByteString.copyFrom(uuidBytes)
        val msgBuilder = UniversalMessage.RoutableMessage.newBuilder()
            .setToDestination(
                UniversalMessage.Destination.newBuilder().setDomain(UniversalMessage.Domain.DOMAIN_INFOTAINMENT)
            )
            .setFromDestination(
                UniversalMessage.Destination.newBuilder()
                    .setRoutingAddress(ByteString.copyFrom(randomBytes(16)))
            )
            .setProtobufMessageAsBytes(ByteString.copyFrom(frame.ciphertext))
            .setSignatureData(sigData)
            .setUuid(uuid)
        if (flags != 0) msgBuilder.flags = flags

        pendingByUuid[uuid] = PendingRequest(frame.tag, label, System.currentTimeMillis())

        val bytes = msgBuilder.build().toByteArray()
        Log.i(TAG, "TX infotainment $label counter=$counter flags=$flags bytes=${bytes.size}")
        connection.send(bytes)
    }

    private suspend fun sendVcsecSigned(unsigned: Vcsec.UnsignedMessage, label: String) {
        val state = domainStates[UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY]
            ?: error("VCSEC session not yet established — call requestSessionInfo first")

        val innerBytes = unsigned.toByteArray()
        state.counter += 1
        val counter = state.counter
        val expiresAt = vehicleClockNow(state) + COMMAND_EXPIRY_SEC

        val mac = state.session.newHmac("authenticated command")
        val tag = Metadata.hmac(mac)
            .addByte(Metadata.Tag.SIGNATURE_TYPE, Signatures.SignatureType.SIGNATURE_TYPE_HMAC_PERSONALIZED.number)
            .addByte(Metadata.Tag.DOMAIN, UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY.number)
            .add(Metadata.Tag.PERSONALIZATION, vin.toByteArray(Charsets.US_ASCII))
            .add(Metadata.Tag.EPOCH, state.epoch)
            .addUint32BE(Metadata.Tag.EXPIRES_AT, expiresAt)
            .addUint32BE(Metadata.Tag.COUNTER, counter)
            .checksum(innerBytes)

        val sigData = Signatures.SignatureData.newBuilder()
            .setSignerIdentity(
                Signatures.KeyIdentity.newBuilder()
                    .setPublicKey(ByteString.copyFrom(identity.publicKeyBytes()))
            )
            .setHMACPersonalizedData(
                Signatures.HMAC_Personalized_Signature_Data.newBuilder()
                    .setEpoch(ByteString.copyFrom(state.epoch))
                    .setCounter(counter)
                    .setExpiresAt(expiresAt)
                    .setTag(ByteString.copyFrom(tag))
            )
            .build()

        val msg = UniversalMessage.RoutableMessage.newBuilder()
            .setToDestination(
                UniversalMessage.Destination.newBuilder().setDomain(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY)
            )
            .setFromDestination(
                UniversalMessage.Destination.newBuilder()
                    .setRoutingAddress(ByteString.copyFrom(randomBytes(16)))
            )
            .setProtobufMessageAsBytes(ByteString.copyFrom(innerBytes))
            .setSignatureData(sigData)
            .setUuid(ByteString.copyFrom(randomBytes(16)))
            .build()

        val bytes = msg.toByteArray()
        Log.i(TAG, "TX $label counter=$counter expiresAt=$expiresAt bytes=${bytes.size}")
        connection.send(bytes)
    }

    private fun vehicleClockNow(state: DomainState): Int {
        val elapsedSec = (System.currentTimeMillis() - state.realTimeAtHandshake) / 1000
        return (state.clockAtHandshake + elapsedSec).toInt()
    }

    private companion object Const {
        const val COMMAND_EXPIRY_SEC = 5
        val VOID: Common.Void = Common.Void.getDefaultInstance()
    }

    /**
     * Ask VCSEC to whitelist this device's BLE identity. Tesla's protocol says the
     * vehicle will respond with `OPERATIONSTATUS_WAIT` until the user taps a
     * registered NFC keycard on the center-console reader, then `OPERATIONSTATUS_OK`.
     *
     * Per [[tesla-ble-flow]]: the order is strict — Connect first, *then* tap card.
     * Calling this method only enqueues the request; the UI must guide the tap.
     */
    suspend fun requestEnrollment(role: Keys.Role = Keys.Role.ROLE_OWNER) {
        sendWhitelistAdd(
            publicKey = identity.publicKeyBytes(),
            formFactor = Vcsec.KeyFormFactor.KEY_FORM_FACTOR_ANDROID_DEVICE,
            role = role,
        )
    }

    /**
     * Whitelist a separate public key as an NFC keycard — used by the HCE
     * "phone-as-keycard" feature. The car will respond `OPERATIONSTATUS_WAIT` and
     * expect a physical NFC tap on the center console; satisfying it requires the
     * caller to have the HCE service armed and the phone held to the console.
     *
     * The current BLE session (signed by [identity]) authorizes the request — same
     * VCSEC path as [requestEnrollment], only the key bytes and form-factor differ.
     */
    suspend fun requestKeycardEnrollment(
        keycardPublicKey: ByteArray,
        role: Keys.Role = Keys.Role.ROLE_DRIVER,
    ) {
        sendWhitelistAdd(
            publicKey = keycardPublicKey,
            formFactor = Vcsec.KeyFormFactor.KEY_FORM_FACTOR_NFC_CARD,
            role = role,
        )
    }

    private suspend fun sendWhitelistAdd(
        publicKey: ByteArray,
        formFactor: Vcsec.KeyFormFactor,
        role: Keys.Role,
    ) {
        _enrollment.value = Enrollment.Requested
        val whitelistOp = Vcsec.WhitelistOperation.newBuilder()
            .setAddKeyToWhitelistAndAddPermissions(
                Vcsec.PermissionChange.newBuilder()
                    .setKey(Vcsec.PublicKey.newBuilder().setPublicKeyRaw(ByteString.copyFrom(publicKey)))
                    .setKeyRole(role)
            )
            .setMetadataForKey(
                Vcsec.KeyMetadata.newBuilder()
                    .setKeyFormFactor(formFactor)
            )
            .build()
        val unsigned = Vcsec.UnsignedMessage.newBuilder()
            .setWhitelistOperation(whitelistOp)
            .build()
        val msg = UniversalMessage.RoutableMessage.newBuilder()
            .setToDestination(
                UniversalMessage.Destination.newBuilder().setDomain(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY)
            )
            .setFromDestination(
                UniversalMessage.Destination.newBuilder()
                    .setRoutingAddress(ByteString.copyFrom(randomBytes(16)))
            )
            .setProtobufMessageAsBytes(ByteString.copyFrom(unsigned.toByteArray()))
            .setUuid(ByteString.copyFrom(randomBytes(16)))
            .build()
        val bytes = msg.toByteArray()
        Log.i(TAG, "TX whitelist-add role=$role form=$formFactor bytes=${bytes.size}")
        connection.send(bytes)
    }

    /**
     * Ask VCSEC for the current [Vcsec.VehicleStatus] (lock, closures, sleep, user presence).
     *
     * This is an unsigned [Vcsec.InformationRequest] with type `GET_STATUS`. The reply
     * arrives as a [Vcsec.FromVCSECMessage] whose `vehicleStatus` sub_message lands in
     * [vehicleStatus]. VCSEC also auto-broadcasts the same message on state changes, so
     * the flow can update without us asking.
     */
    suspend fun requestVehicleStatus() {
        val infoReq = Vcsec.InformationRequest.newBuilder()
            .setInformationRequestType(Vcsec.InformationRequestType.INFORMATION_REQUEST_TYPE_GET_STATUS)
            .build()
        val unsigned = Vcsec.UnsignedMessage.newBuilder()
            .setInformationRequest(infoReq)
            .build()
        val msg = UniversalMessage.RoutableMessage.newBuilder()
            .setToDestination(
                UniversalMessage.Destination.newBuilder().setDomain(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY)
            )
            .setFromDestination(
                UniversalMessage.Destination.newBuilder()
                    .setRoutingAddress(ByteString.copyFrom(randomBytes(16)))
            )
            .setProtobufMessageAsBytes(ByteString.copyFrom(unsigned.toByteArray()))
            .setUuid(ByteString.copyFrom(randomBytes(16)))
            .build()
        val bytes = msg.toByteArray()
        Log.i(TAG, "TX info-request GET_STATUS bytes=${bytes.size}")
        connection.send(bytes)
    }

    /** Send a session_info_request for [domain] over the connection. */
    suspend fun requestSessionInfo(domain: UniversalMessage.Domain) {
        _status.value = Status.Requested(domain)
        val msg = UniversalMessage.RoutableMessage.newBuilder()
            .setToDestination(
                UniversalMessage.Destination.newBuilder().setDomain(domain)
            )
            .setFromDestination(
                UniversalMessage.Destination.newBuilder()
                    .setRoutingAddress(ByteString.copyFrom(randomBytes(16)))
            )
            .setSessionInfoRequest(
                UniversalMessage.SessionInfoRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(identity.publicKeyBytes()))
                    .setChallenge(ByteString.copyFrom(randomBytes(16)))
            )
            .setUuid(ByteString.copyFrom(randomBytes(16)))
            .build()
        val bytes = msg.toByteArray()
        Log.i(TAG, "TX session_info_request domain=$domain bytes=${bytes.size}")
        connection.send(bytes)
    }

    private fun handleIncoming(bytes: ByteArray) {
        _rxCount.value = _rxCount.value + 1
        val msg = try {
            UniversalMessage.RoutableMessage.parseFrom(bytes)
        } catch (e: Throwable) {
            Log.w(TAG, "parse RoutableMessage failed: ${e.message} raw=${bytes.toHex()}")
            return
        }
        val fromDom = if (msg.fromDestination.hasDomain()) msg.fromDestination.domain.name else "addr"
        Log.i(TAG, "RX from=$fromDom payload=${msg.payloadCase}")

        if (msg.payloadCase == UniversalMessage.RoutableMessage.PayloadCase.PROTOBUF_MESSAGE_AS_BYTES) {
            val fromDomain = if (msg.fromDestination.hasDomain()) msg.fromDestination.domain else null
            if (fromDomain == UniversalMessage.Domain.DOMAIN_INFOTAINMENT) {
                handleInfotainmentResponse(msg)
            } else {
                handleVcsecMessage(msg.protobufMessageAsBytes.toByteArray())
            }
            return
        }
        if (msg.payloadCase != UniversalMessage.RoutableMessage.PayloadCase.SESSION_INFO) {
            // No payload (e.g. infotainment rejecting a signed command). Surface the
            // signedMessageStatus so we can see which fault the car returned, and
            // discard any pending entry so we don't leak it.
            if (msg.hasSignedMessageStatus()) {
                val status = msg.signedMessageStatus
                val pending = pendingByUuid.remove(msg.requestUuid)
                val label = pending?.label ?: "?"
                Log.w(
                    TAG,
                    "RX $fromDom no payload — fault=${status.signedMessageFault} op=${status.operationStatus} for $label",
                )
            }
            return
        }
        val info = try {
            Signatures.SessionInfo.parseFrom(msg.sessionInfo)
        } catch (e: Throwable) {
            Log.w(TAG, "parse SessionInfo failed: ${e.message}")
            _status.value = Status.Failed("invalid SessionInfo: ${e.message}")
            return
        }
        Log.i(
            TAG,
            "SessionInfo status=${info.status} counter=${info.counter} clock=${info.clockTime} epoch=${info.epoch.toByteArray().toHex()}",
        )

        val domain = if (msg.fromDestination.hasDomain()) {
            msg.fromDestination.domain
        } else {
            UniversalMessage.Domain.DOMAIN_BROADCAST
        }

        // Unpaired keys get a SessionInfo with status=KEY_NOT_ON_WHITELIST and an
        // empty publicKey field — that's a normal "not on whitelist yet" reply, not
        // a malformed message. Surface it as Established with the non-OK enum so
        // the UI can offer Enroll; just don't try to derive an ECDH session.
        val pubBytes = info.publicKey.toByteArray()
        if (info.status != Signatures.Session_Info_Status.SESSION_INFO_STATUS_OK) {
            Log.i(TAG, "SessionInfo non-OK (status=${info.status}, pubKey=${pubBytes.size}B) — skipping ECDH")
            _status.value = Status.Established(
                domain = domain,
                counter = info.counter,
                epochHex = info.epoch.toByteArray().toHex(),
                vehicleClockSec = info.clockTime.toLong() and 0xFFFFFFFFL,
                statusEnum = info.status,
            )
            return
        }

        if (pubBytes.size != 65 || pubBytes[0] != 0x04.toByte()) {
            Log.w(TAG, "SessionInfo publicKey bad: size=${pubBytes.size}")
            _status.value = Status.Failed("vehicle public key wrong size: ${pubBytes.size}")
            return
        }

        val derived = try {
            val pub = Session.decodePublicKey(pubBytes)
            identity.deriveSession(pub)
        } catch (e: Throwable) {
            Log.w(TAG, "deriveSession failed: ${e.message}")
            _status.value = Status.Failed("ECDH failed: ${e.message}")
            return
        }

        sessions[domain] = derived
        domainStates[domain] = DomainState(
            session = derived,
            epoch = info.epoch.toByteArray(),
            counter = info.counter,
            clockAtHandshake = info.clockTime.toLong() and 0xFFFFFFFFL,
            realTimeAtHandshake = System.currentTimeMillis(),
        )
        _status.value = Status.Established(
            domain = domain,
            counter = info.counter,
            epochHex = info.epoch.toByteArray().toHex(),
            vehicleClockSec = info.clockTime.toLong() and 0xFFFFFFFFL,
            statusEnum = info.status,
        )
        Log.i(TAG, "Session derived for $domain K=${derived.key.toHex()}")

        // Once Infotainment is up, fetch the data the UI needs (range, window state).
        if (domain == UniversalMessage.Domain.DOMAIN_INFOTAINMENT &&
            info.status == Signatures.Session_Info_Status.SESSION_INFO_STATUS_OK
        ) {
            scope.launch {
                runCatching { requestVehicleData() }
                    .onFailure { Log.w(TAG, "auto GET_VEHICLE_DATA failed: ${it.message}") }
            }
        }
    }

    private fun handleVcsecMessage(payload: ByteArray) {
        val msg = try {
            Vcsec.FromVCSECMessage.parseFrom(payload)
        } catch (e: Throwable) {
            Log.w(TAG, "parse FromVCSECMessage failed: ${e.message}")
            return
        }
        Log.i(TAG, "VCSEC sub=${msg.subMessageCase}")
        if (msg.subMessageCase == Vcsec.FromVCSECMessage.SubMessageCase.VEHICLESTATUS) {
            _vehicleStatus.value = VehicleStatusSnapshot(msg.vehicleStatus, System.currentTimeMillis())
            return
        }
        if (msg.subMessageCase != Vcsec.FromVCSECMessage.SubMessageCase.COMMANDSTATUS) return
        val cmd = msg.commandStatus
        if (cmd.subMessageCase != Vcsec.CommandStatus.SubMessageCase.WHITELISTOPERATIONSTATUS) {
            Log.i(TAG, "CommandStatus op=${cmd.operationStatus} sub=${cmd.subMessageCase}")
            return
        }
        val wls = cmd.whitelistOperationStatus
        Log.i(
            TAG,
            "WhitelistOpStatus op=${cmd.operationStatus} info=${wls.whitelistOperationInformation}",
        )
        _enrollment.value = when (cmd.operationStatus) {
            Vcsec.OperationStatus_E.OPERATIONSTATUS_OK ->
                Enrollment.Success
            Vcsec.OperationStatus_E.OPERATIONSTATUS_WAIT ->
                Enrollment.AwaitingKeycard
            Vcsec.OperationStatus_E.OPERATIONSTATUS_ERROR ->
                Enrollment.Failed(wls.whitelistOperationInformation.name)
            else -> Enrollment.Failed("unknown op status: ${cmd.operationStatus}")
        }
        // Just landed on the whitelist — re-handshake both domains so the next
        // session_info brings back a valid publicKey (lets us derive ECDH keys),
        // and pull initial vehicle status + data without waiting for the user to
        // bounce out and re-enter the car view.
        if (cmd.operationStatus == Vcsec.OperationStatus_E.OPERATIONSTATUS_OK) {
            scope.launch {
                runCatching { requestSessionInfo(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY) }
                    .onFailure { Log.w(TAG, "post-enroll VCSEC session_info failed: ${it.message}") }
                runCatching { requestSessionInfo(UniversalMessage.Domain.DOMAIN_INFOTAINMENT) }
                    .onFailure { Log.w(TAG, "post-enroll Infotainment session_info failed: ${it.message}") }
                runCatching { requestVehicleStatus() }
                    .onFailure { Log.w(TAG, "post-enroll GET_STATUS failed: ${it.message}") }
            }
        }
    }

    /**
     * Decrypt an Infotainment reply addressed to one of our outstanding requests.
     *
     * Per Tesla's protocol.md, the AES_GCM_Response AAD is the SHA-256 of:
     *   SIG_TYPE(AES_GCM_RESPONSE) ‖ DOMAIN ‖ PERSONALIZATION(VIN) ‖ COUNTER
     *   ‖ FLAGS (of the response, *always* present) ‖ REQUEST_HASH
     *   ‖ FAULT (only if non-zero) ‖ TAG_END
     * REQUEST_HASH = SIG_TYPE_byte_of_request ‖ request_gcm_tag(16B). Skipping any of
     * DOMAIN/PERSONALIZATION/COUNTER/FLAGS causes GCM auth-tag mismatch and a silent
     * BAD_DECRYPT, which is what bit us in v0.1.2.
     */
    private fun handleInfotainmentResponse(msg: UniversalMessage.RoutableMessage) {
        val state = domainStates[UniversalMessage.Domain.DOMAIN_INFOTAINMENT]
        if (state == null) {
            Log.w(TAG, "Infotainment RX but no session established yet — dropping")
            return
        }
        val sigCase = msg.signatureData.sigTypeCase
        if (sigCase != Signatures.SignatureData.SigTypeCase.AES_GCM_RESPONSE_DATA) {
            Log.w(TAG, "Infotainment RX without AES_GCM_Response sig data (got $sigCase)")
            return
        }
        val respSig = msg.signatureData.getAESGCMResponseData()
        val pending = pendingByUuid.remove(msg.requestUuid)
        if (pending == null) {
            Log.w(TAG, "Infotainment RX for unknown request_uuid=${msg.requestUuid.toByteArray().toHex()}")
            return
        }

        val requestHash = byteArrayOf(
            Signatures.SignatureType.SIGNATURE_TYPE_AES_GCM_PERSONALIZED.number.toByte(),
        ) + pending.tag
        val fault = msg.signedMessageStatus.signedMessageFault.number
        // Mirrors `responseMetadata` in vehicle-command/internal/authentication/peer.go:
        // every field is always included (no zero-skip), and FAULT/COUNTER/FLAGS go in as
        // uint32 BE — not as single bytes. Missing FAULT or downcasting it to one byte
        // makes the AAD mismatch and GCM fails with BAD_DECRYPT even on a successful
        // (fault=0) response.
        val aad = Metadata.sha256()
            .addByte(
                Metadata.Tag.SIGNATURE_TYPE,
                Signatures.SignatureType.SIGNATURE_TYPE_AES_GCM_RESPONSE.number,
            )
            .addByte(Metadata.Tag.DOMAIN, UniversalMessage.Domain.DOMAIN_INFOTAINMENT.number)
            .add(Metadata.Tag.PERSONALIZATION, vin.toByteArray(Charsets.US_ASCII))
            .addUint32BE(Metadata.Tag.COUNTER, respSig.counter)
            .addUint32BE(Metadata.Tag.FLAGS, msg.flags)
            .add(Metadata.Tag.REQUEST_HASH, requestHash)
            .addUint32BE(Metadata.Tag.FAULT, fault)
            .checksum()

        val ciphertext = msg.protobufMessageAsBytes.toByteArray()
        val plaintext = try {
            state.session.decrypt(
                respSig.nonce.toByteArray(),
                ciphertext,
                respSig.tag.toByteArray(),
                aad,
            )
        } catch (e: Throwable) {
            Log.w(
                TAG,
                "Infotainment decrypt failed for ${pending.label}: ${e.message} " +
                    "resp.counter=${respSig.counter} msg.flags=${msg.flags} " +
                    "msgStatus.op=${msg.signedMessageStatus.operationStatus} fault=$fault " +
                    "reqHash=${requestHash.toHex()} aad=${aad.toHex()}",
            )
            return
        }

        val response = try {
            CarServer.Response.parseFrom(plaintext)
        } catch (e: Throwable) {
            Log.w(TAG, "Infotainment Response parse failed for ${pending.label}: ${e.message}")
            return
        }

        Log.i(
            TAG,
            "RX infotainment ${pending.label} status=${response.actionStatus.result} payload=${response.responseMsgCase}",
        )

        if (response.responseMsgCase == CarServer.Response.ResponseMsgCase.VEHICLEDATA) {
            mergeVehicleData(response.vehicleData)
        }
    }

    /**
     * Each [requestVehicleData] now arrives in pieces (charge / climate / tires / media)
     * because the whole-vehicle response trips the car's response-MTU limit. Merge each
     * incoming subset into the cached snapshot rather than overwriting; that way a fresh
     * ChargeState doesn't blank out climate data the UI is already showing.
     */
    private fun mergeVehicleData(incoming: Vehicle.VehicleData) {
        val prev = _vehicleData.value?.data
        val merged = if (prev == null) {
            incoming
        } else {
            prev.toBuilder().apply {
                if (incoming.hasChargeState()) chargeState = incoming.chargeState
                if (incoming.hasClimateState()) climateState = incoming.climateState
                if (incoming.hasTirePressureState()) tirePressureState = incoming.tirePressureState
                if (incoming.hasMediaState()) mediaState = incoming.mediaState
                if (incoming.hasClosuresState()) closuresState = incoming.closuresState
            }.build()
        }
        _vehicleData.value = VehicleDataSnapshot(merged, System.currentTimeMillis())
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    data class VehicleStatusSnapshot(
        val status: Vcsec.VehicleStatus,
        val receivedAtMs: Long,
    )

    data class VehicleDataSnapshot(
        val data: Vehicle.VehicleData,
        val receivedAtMs: Long,
    )
}
