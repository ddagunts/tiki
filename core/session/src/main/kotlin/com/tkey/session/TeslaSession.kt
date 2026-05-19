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
    private val vin: String,
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

    /**
     * Ask the car for its current [Vehicle.VehicleData]. We request only the subsets the
     * UI actually renders (charge state for range, closure state for window/door positions).
     */
    suspend fun requestVehicleData() = sendInfotainmentAction(
        label = "GET_VEHICLE_DATA",
        build = {
            setGetVehicleData(
                CarServer.GetVehicleData.newBuilder()
                    .setGetChargeState(CarServer.GetChargeState.getDefaultInstance())
                    .setGetClosuresState(CarServer.GetClosuresState.getDefaultInstance())
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
     * Ask VCSEC to whitelist this device's public key. Tesla's protocol says the
     * vehicle will respond with `OPERATIONSTATUS_WAIT` until the user taps a
     * registered NFC keycard on the center-console reader, then `OPERATIONSTATUS_OK`.
     *
     * Per [[tesla-ble-flow]]: the order is strict — Connect first, *then* tap card.
     * Calling this method only enqueues the request; the UI must guide the tap.
     */
    suspend fun requestEnrollment(role: Keys.Role = Keys.Role.ROLE_OWNER) {
        _enrollment.value = Enrollment.Requested
        val pkRaw = identity.publicKeyBytes()
        val whitelistOp = Vcsec.WhitelistOperation.newBuilder()
            .setAddKeyToWhitelistAndAddPermissions(
                Vcsec.PermissionChange.newBuilder()
                    .setKey(Vcsec.PublicKey.newBuilder().setPublicKeyRaw(ByteString.copyFrom(pkRaw)))
                    .setKeyRole(role)
            )
            .setMetadataForKey(
                Vcsec.KeyMetadata.newBuilder()
                    .setKeyFormFactor(Vcsec.KeyFormFactor.KEY_FORM_FACTOR_ANDROID_DEVICE)
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
        Log.i(TAG, "TX whitelist-add role=$role bytes=${bytes.size}")
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

        val pubBytes = info.publicKey.toByteArray()
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

        val domain = if (msg.fromDestination.hasDomain()) {
            msg.fromDestination.domain
        } else {
            UniversalMessage.Domain.DOMAIN_BROADCAST
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
    }

    /**
     * Decrypt an Infotainment reply addressed to one of our outstanding requests.
     *
     * AAD for [SIGNATURE_TYPE_AES_GCM_RESPONSE]:
     *   SHA-256( SIG_TYPE=9 ‖ REQUEST_HASH=[sig_type_byte_of_request ‖ request_gcm_tag(16B)]
     *            [‖ FAULT=<byte> if non-zero] ‖ TAG_END )
     *
     * If decryption succeeds and the payload contains [Vehicle.VehicleData], publish it
     * to [vehicleData]. Otherwise log and drop — non-data Responses (windows, volume) only
     * need their `actionStatus` for diagnostics, which we already log.
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
        val aadBuilder = Metadata.sha256()
            .addByte(
                Metadata.Tag.SIGNATURE_TYPE,
                Signatures.SignatureType.SIGNATURE_TYPE_AES_GCM_RESPONSE.number,
            )
            .add(Metadata.Tag.REQUEST_HASH, requestHash)
        if (fault != 0) aadBuilder.addByte(Metadata.Tag.FAULT, fault)
        val aad = aadBuilder.checksum()

        val ciphertext = msg.protobufMessageAsBytes.toByteArray()
        val plaintext = try {
            state.session.decrypt(
                respSig.nonce.toByteArray(),
                ciphertext,
                respSig.tag.toByteArray(),
                aad,
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Infotainment decrypt failed for ${pending.label}: ${e.message}")
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
            _vehicleData.value = VehicleDataSnapshot(response.vehicleData, System.currentTimeMillis())
        }
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
