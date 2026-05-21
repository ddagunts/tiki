package com.tkey.ui

import android.content.Context
import android.util.Log
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.vcsec.Vcsec
import com.tkey.ble.CarConnection
import com.tkey.ble.CarScanner
import com.tkey.crypto.Identity
import com.tkey.session.TeslaSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "TKey.Ctrl"

/**
 * Drives the full scan → connect → session-handshake pipeline for one car
 * and auto-reconnects whenever the link drops. The UI selects a car; this
 * class handles everything else until the user explicitly stops.
 */
class CarController(
    private val ctx: Context,
    private val identity: Identity,
) {
    sealed class Phase {
        data object Idle : Phase()
        data class Scanning(val attempt: Int) : Phase()
        data object Connecting : Phase()
        data object Handshaking : Phase()
        data object Ready : Phase()
        data class Reconnecting(
            val reason: String,
            val attempt: Int,
            val remainingMs: Long,
        ) : Phase()
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _connection = MutableStateFlow<CarConnection?>(null)
    val connection: StateFlow<CarConnection?> = _connection.asStateFlow()

    private val _session = MutableStateFlow<TeslaSession?>(null)
    val session: StateFlow<TeslaSession?> = _session.asStateFlow()

    private val scope = MainScope()
    private var loopJob: Job? = null
    private var infotainmentJob: Job? = null

    /**
     * @param pairedProvider returns true if this car has completed first-time
     * enrollment. When false the controller still scans + connects, but stops
     * short of the session_info handshake — the UI drives Session / Enroll
     * manually from the settings screen. Read at every iteration so the loop
     * picks up "paired" state changes between reconnects.
     */
    fun start(vin: String, pairedProvider: () -> Boolean = { true }) {
        // Chain the new loop after the old one's `finally { cleanupSession() }` so we don't
        // race a still-disconnecting connection from the previous iteration.
        val previous = loopJob
        loopJob = scope.launch {
            previous?.cancelAndJoin()
            runLoop(vin, pairedProvider)
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        cleanupSession()
        _phase.value = Phase.Idle
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private suspend fun runLoop(vin: String, pairedProvider: () -> Boolean) {
        var failAttempt = 0
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    runOnce(vin, failAttempt, pairedProvider())
                    failAttempt = 0
                    awaitDisconnect()
                    Log.i(TAG, "Link dropped — retrying scan")
                    cleanupSession()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    cleanupSession()
                    failAttempt++
                    val delayMs = backoffMs(failAttempt)
                    val reason = e.message ?: e::class.simpleName.orEmpty()
                    Log.w(TAG, "Iteration failed ($reason); retrying in ${delayMs}ms (attempt $failAttempt)")
                    countdown(reason, failAttempt, delayMs)
                }
            }
        } finally {
            cleanupSession()
        }
    }

    private suspend fun runOnce(vin: String, failAttempt: Int, paired: Boolean) {
        _phase.value = Phase.Scanning(attempt = failAttempt + 1)
        val beacon = CarScanner(ctx).discover(vin)
            .mapNotNull { (it as? CarScanner.Event.Match)?.beacon }
            .first()

        _phase.value = Phase.Connecting
        val conn = CarConnection.fromMac(ctx, beacon.address)
        _connection.value = conn
        conn.connect()
        val transport = conn.state.first {
            it is CarConnection.State.Ready ||
                it is CarConnection.State.Failed ||
                it is CarConnection.State.Disconnected
        }
        if (transport !is CarConnection.State.Ready) {
            error("Transport ended in $transport")
        }

        val s = TeslaSession(identity, conn, vin)
        s.start()
        _session.value = s

        if (!paired) {
            // Unpaired: don't auto-handshake — let the user drive Session/Enroll from
            // settings. Repeatedly firing session_info against an unwhitelisted key
            // doesn't go anywhere useful and looks like a retry storm to the user.
            _phase.value = Phase.Ready
            return
        }

        _phase.value = Phase.Handshaking
        s.requestSessionInfo(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY)

        // Race handshake against transport — don't hang if the link drops mid-handshake.
        val outcome = combine(s.status, conn.state) { ss, cs -> ss to cs }
            .first { (ss, cs) ->
                ss is TeslaSession.Status.Established ||
                    ss is TeslaSession.Status.Failed ||
                    cs !is CarConnection.State.Ready
            }
        val (sessStatus, transState) = outcome
        if (transState !is CarConnection.State.Ready) {
            error("Transport dropped during handshake: $transState")
        }
        if (sessStatus is TeslaSession.Status.Failed) {
            error("Session handshake failed: ${sessStatus.reason}")
        }
        _phase.value = Phase.Ready

        runCatching { s.requestVehicleStatus() }
            .onFailure { Log.w(TAG, "auto GET_STATUS failed: ${it.message}") }

        // Infotainment lives in a separate process that's offline while the car sleeps.
        // A single session_info_request would be dropped silently; instead, run a
        // maintenance loop that keeps retrying (and sends one wake if VCSEC reports
        // the car asleep) until DOMAIN_INFOTAINMENT replies or the link drops.
        infotainmentJob?.cancel()
        infotainmentJob = scope.launch { ensureInfotainmentSession(s, conn) }
    }

    private suspend fun ensureInfotainmentSession(s: TeslaSession, conn: CarConnection) {
        var attempt = 0
        var wakeFired = false
        while (currentCoroutineContext().isActive) {
            if (conn.state.value !is CarConnection.State.Ready) return
            if (s.isReady(UniversalMessage.Domain.DOMAIN_INFOTAINMENT)) return
            // If infotainment already answered but with a non-OK status (e.g., this key
            // isn't whitelisted for it), retrying won't change anything — bail out.
            val st = s.status.value
            if (st is TeslaSession.Status.Established &&
                st.domain == UniversalMessage.Domain.DOMAIN_INFOTAINMENT &&
                st.statusEnum != Signatures.Session_Info_Status.SESSION_INFO_STATUS_OK
            ) {
                Log.i(TAG, "infotainment session_info returned ${st.statusEnum} — not retrying")
                return
            }
            val asleep = s.vehicleStatus.value?.status?.vehicleSleepStatus ==
                Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP
            if (asleep && !wakeFired) {
                runCatching { s.wakeVehicle() }
                    .onFailure { Log.w(TAG, "wake-vehicle failed: ${it.message}") }
                wakeFired = true
                delay(2_000L)
                continue
            }
            attempt++
            runCatching { s.requestSessionInfo(UniversalMessage.Domain.DOMAIN_INFOTAINMENT) }
                .onFailure { Log.w(TAG, "infotainment session_info retry $attempt: ${it.message}") }
            val delayMs = when {
                attempt <= 2 -> 3_000L
                attempt <= 4 -> 6_000L
                attempt <= 7 -> 12_000L
                else -> 30_000L
            }
            delay(delayMs)
        }
    }

    private suspend fun awaitDisconnect() {
        val conn = _connection.value ?: return
        conn.state.first { it !is CarConnection.State.Ready }
    }

    private fun cleanupSession() {
        infotainmentJob?.cancel()
        infotainmentJob = null
        _session.value?.stop()
        _session.value = null
        _connection.value?.disconnect()
        _connection.value = null
    }

    private suspend fun countdown(reason: String, attempt: Int, delayMs: Long) {
        var remaining = delayMs
        while (remaining > 0) {
            _phase.value = Phase.Reconnecting(reason, attempt, remaining)
            val step = if (remaining > 1000L) 1000L else remaining
            delay(step)
            remaining -= step
        }
    }

    private fun backoffMs(attempt: Int): Long = when {
        attempt <= 1 -> 1_000L
        attempt == 2 -> 2_000L
        attempt == 3 -> 5_000L
        attempt <= 5 -> 15_000L
        else -> 30_000L
    }
}
