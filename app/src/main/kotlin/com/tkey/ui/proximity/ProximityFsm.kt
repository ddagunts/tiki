package com.tkey.ui.proximity

/**
 * Per-car state machine deciding when to fire a lock or unlock based on the running
 * RSSI of the car's BLE beacon. Two stable states (FAR, NEAR) with hysteresis: enter NEAR
 * (and fire Unlock) when an EMA-smoothed RSSI sits above [ProximityConfig.unlockRssi] for
 * [ProximityConfig.enterDwellMs]; enter FAR (and fire Lock) when it sits below
 * [ProximityConfig.lockRssi] for [ProximityConfig.exitDwellMs].
 *
 * Critical: silence (no beacons observed) does NOT push the EMA. Tesla cars only advertise
 * while sleeping, so a silent radio after an unlock usually means the user got in and the
 * car woke up — not that the user walked away. Treating silence as "far" would falsely
 * lock the user in moments after they unlocked.
 *
 * Fallback: if NEAR persists past [STALE_NEAR_TIMEOUT_MS] without any beacon (the user
 * likely drove off and parked somewhere we can't see), we transition back to FAR and emit
 * a Lock — it'll either succeed (car slept at the new location and is beaconing weakly)
 * or fail to find a beacon and no-op.
 *
 * The FSM is purely a decision oracle — it knows nothing about BLE, threads, or Tesla.
 * Drive it by calling [feed] for every beacon hit and [tick] periodically so that dwell
 * timers and the stale-NEAR fallback can fire even between samples.
 */
class ProximityFsm(initialCfg: ProximityConfig) {

    enum class State { Far, Near }
    enum class Action { Unlock, Lock }

    @Volatile private var cfg: ProximityConfig = initialCfg
    private var state: State = State.Far
    private var ema: Double? = null
    private var enterCandidateMs: Long? = null
    private var exitCandidateMs: Long? = null
    private var lastActionMs: Long = Long.MIN_VALUE / 2
    private var lastSampleMs: Long = Long.MIN_VALUE / 2

    val currentState: State get() = state
    val emaRssi: Double? get() = ema
    val lastSeenMs: Long get() = lastSampleMs

    /**
     * Swap in a new [ProximityConfig] without resetting EMA / state / timers — used when the
     * user nudges a threshold slider so that the live readout doesn't blink back to "no
     * signal yet" mid-calibration.
     */
    fun updateConfig(newCfg: ProximityConfig) {
        cfg = newCfg
    }

    /** Feed a real beacon RSSI observed at [nowMs]. Returns an Action if a transition fires. */
    fun feed(rssi: Int, nowMs: Long): Action? {
        ema = ema?.let { (1 - EMA_ALPHA) * it + EMA_ALPHA * rssi } ?: rssi.toDouble()
        lastSampleMs = nowMs
        return evaluate(nowMs)
    }

    /**
     * Re-evaluates dwell timers without injecting a synthetic sample, so the FSM can fire an
     * Unlock that became eligible during a brief gap between beacons. Also drives the
     * stale-NEAR fallback that converts a long beacon-less NEAR into a Lock attempt.
     */
    fun tick(nowMs: Long): Action? {
        if (state == State.Near && lastSampleMs > Long.MIN_VALUE / 2 &&
            nowMs - lastSampleMs >= STALE_NEAR_TIMEOUT_MS &&
            nowMs - lastActionMs >= COOLDOWN_MS
        ) {
            state = State.Far
            enterCandidateMs = null
            exitCandidateMs = null
            lastActionMs = nowMs
            return Action.Lock
        }
        return evaluate(nowMs)
    }

    private fun evaluate(nowMs: Long): Action? {
        val smoothed = ema ?: return null
        return when (state) {
            State.Far -> evaluateEnter(smoothed, nowMs)
            State.Near -> evaluateExit(smoothed, nowMs)
        }
    }

    private fun evaluateEnter(smoothed: Double, nowMs: Long): Action? {
        if (smoothed >= cfg.unlockRssi) {
            val start = enterCandidateMs ?: nowMs.also { enterCandidateMs = it }
            if (nowMs - start >= cfg.enterDwellMs && nowMs - lastActionMs >= COOLDOWN_MS) {
                state = State.Near
                enterCandidateMs = null
                exitCandidateMs = null
                lastActionMs = nowMs
                return Action.Unlock
            }
        } else {
            enterCandidateMs = null
        }
        return null
    }

    private fun evaluateExit(smoothed: Double, nowMs: Long): Action? {
        if (smoothed <= cfg.lockRssi) {
            val start = exitCandidateMs ?: nowMs.also { exitCandidateMs = it }
            if (nowMs - start >= cfg.exitDwellMs && nowMs - lastActionMs >= COOLDOWN_MS) {
                state = State.Far
                enterCandidateMs = null
                exitCandidateMs = null
                lastActionMs = nowMs
                return Action.Lock
            }
        } else {
            exitCandidateMs = null
        }
        return null
    }

    companion object {
        private const val EMA_ALPHA = 0.4
        private const val COOLDOWN_MS = 60_000L
        private const val STALE_NEAR_TIMEOUT_MS = 10 * 60_000L
    }
}
