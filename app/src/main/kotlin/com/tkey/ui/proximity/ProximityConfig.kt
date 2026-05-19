package com.tkey.ui.proximity

import org.json.JSONObject

/**
 * Per-car proximity-unlock configuration. RSSI values are in dBm (more negative = farther).
 * Hysteresis is enforced via the gap between [unlockRssi] (entering NEAR) and [lockRssi]
 * (entering FAR); the lock threshold must be at least 5 dBm weaker than the unlock threshold.
 */
data class ProximityConfig(
    val enabled: Boolean = false,
    val unlockRssi: Int = DEFAULT_UNLOCK_RSSI,
    val lockRssi: Int = DEFAULT_LOCK_RSSI,
    val enterDwellMs: Long = DEFAULT_ENTER_DWELL_MS,
    val exitDwellMs: Long = DEFAULT_EXIT_DWELL_MS,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
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

        fun fromJson(obj: JSONObject): ProximityConfig = ProximityConfig(
            enabled = obj.optBoolean("enabled", false),
            unlockRssi = obj.optInt("unlockRssi", DEFAULT_UNLOCK_RSSI),
            lockRssi = obj.optInt("lockRssi", DEFAULT_LOCK_RSSI),
            enterDwellMs = obj.optLong("enterDwellMs", DEFAULT_ENTER_DWELL_MS),
            exitDwellMs = obj.optLong("exitDwellMs", DEFAULT_EXIT_DWELL_MS),
        )
    }
}
