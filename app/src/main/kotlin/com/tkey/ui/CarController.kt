package com.tkey.ui

import android.content.Context
import android.util.Log
import com.tesla.generated.universalmessage.UniversalMessage
import com.tkey.ble.CarConnection
import com.tkey.ble.CarScanner
import com.tkey.crypto.Identity
import com.tkey.session.TeslaSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
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

    fun start(vin: String) {
        loopJob?.cancel()
        loopJob = scope.launch { runLoop(vin) }
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

    private suspend fun runLoop(vin: String) {
        var failAttempt = 0
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    runOnce(vin, failAttempt)
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

    private suspend fun runOnce(vin: String, failAttempt: Int) {
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

        _phase.value = Phase.Handshaking
        val s = TeslaSession(identity, conn, vin)
        s.start()
        _session.value = s
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
        runCatching { s.requestSessionInfo(UniversalMessage.Domain.DOMAIN_INFOTAINMENT) }
            .onFailure { Log.w(TAG, "auto Infotainment session_info failed: ${it.message}") }
    }

    private suspend fun awaitDisconnect() {
        val conn = _connection.value ?: return
        conn.state.first { it !is CarConnection.State.Ready }
    }

    private fun cleanupSession() {
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
