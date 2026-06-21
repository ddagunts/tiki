package com.tkey.ui.proximity

import org.json.JSONObject

/**
 * Per-car proximity configuration. RSSI values are in dBm (more negative = farther).
 * Hysteresis is enforced via the gap between [unlockRssi] (entering NEAR) and [lockRssi]
 * (entering FAR); the lock threshold must be at least 5 dBm weaker than the unlock threshold.
 *
 * [unlockEnabled] and [lockEnabled] independently gate each action: the FSM always tracks
 * NEAR/FAR state (so it can re-unlock on the next approach), but an action is only dispatched
 * when its flag is true.
 */
data class ProximityConfig(
    val enabled: Boolean = false,
    val unlockEnabled: Boolean = true,
    val lockEnabled: Boolean = true,
    val unlockRssi: Int = DEFAULT_UNLOCK_RSSI,
    val lockRssi: Int = DEFAULT_LOCK_RSSI,
    val enterDwellMs: Long = DEFAULT_ENTER_DWELL_MS,
    val exitDwellMs: Long = DEFAULT_EXIT_DWELL_MS,
) {
    init {
        require(unlockRssi in MIN_RSSI..MAX_RSSI) { "unlockRssi out of range: $unlockRssi" }
        require(lockRssi in MIN_RSSI..MAX_RSSI) { "lockRssi out of range: $lockRssi" }
        require(unlockRssi - lockRssi >= MIN_HYSTERESIS_DB) {
            "lockRssi ($lockRssi) must be at least $MIN_HYSTERESIS_DB dBm weaker than unlockRssi ($unlockRssi)"
        }
        require(enterDwellMs in 100L..60_000L) { "enterDwellMs out of range: $enterDwellMs" }
        require(exitDwellMs in 1_000L..600_000L) { "exitDwellMs out of range: $exitDwellMs" }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("unlockEnabled", unlockEnabled)
        .put("lockEnabled", lockEnabled)
        .put("unlockRssi", unlockRssi)
        .put("lockRssi", lockRssi)
        .put("enterDwellMs", enterDwellMs)
        .put("exitDwellMs", exitDwellMs)

    companion object {
        const val DEFAULT_UNLOCK_RSSI = -65
        const val DEFAULT_LOCK_RSSI = -90
        const val DEFAULT_ENTER_DWELL_MS = 1500L
        const val DEFAULT_EXIT_DWELL_MS = 30_000L

        const val MIN_RSSI = -110
        const val MAX_RSSI = -30
        const val MIN_HYSTERESIS_DB = 5

        /**
         * Tolerant parser: clamp every field into the supported range and repair an inverted
         * threshold pair rather than throwing. Used for SharedPreferences round-trips where
         * stale or hand-edited values shouldn't crash the app on launch.
         */
        fun fromJson(obj: JSONObject): ProximityConfig {
            val unlock = obj.optInt("unlockRssi", DEFAULT_UNLOCK_RSSI)
                .coerceIn(MIN_RSSI + MIN_HYSTERESIS_DB, MAX_RSSI)
            val lock = obj.optInt("lockRssi", DEFAULT_LOCK_RSSI)
                .coerceIn(MIN_RSSI, unlock - MIN_HYSTERESIS_DB)
            return ProximityConfig(
                enabled = obj.optBoolean("enabled", false),
                unlockEnabled = obj.optBoolean("unlockEnabled", true),
                lockEnabled = obj.optBoolean("lockEnabled", true),
                unlockRssi = unlock,
                lockRssi = lock,
                enterDwellMs = obj.optLong("enterDwellMs", DEFAULT_ENTER_DWELL_MS)
                    .coerceIn(100L, 60_000L),
                exitDwellMs = obj.optLong("exitDwellMs", DEFAULT_EXIT_DWELL_MS)
                    .coerceIn(1_000L, 600_000L),
            )
        }
    }
}
