package com.tkey.session

import android.util.Log
import com.google.protobuf.ByteString
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
            handleVcsecMessage(msg.protobufMessageAsBytes.toByteArray())
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

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    data class VehicleStatusSnapshot(
        val status: Vcsec.VehicleStatus,
        val receivedAtMs: Long,
    )
}
