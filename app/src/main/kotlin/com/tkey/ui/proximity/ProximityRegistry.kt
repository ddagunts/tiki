package com.tkey.ui.proximity

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.tkey.ui.CarStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide bridge between the proximity settings UI and the running [ProximityService].
 *
 * The UI calls [refresh] after toggling any car's [ProximityConfig]; that re-reads the
 * persisted enabled set and starts or stops the service accordingly. The service publishes
 * back into [live] and [serviceState] so the settings screen can show real-time RSSI for
 * calibration. Configs/live state survive service restarts as long as the process lives.
 */
object ProximityRegistry {

    enum class ServiceState { Stopped, Scanning, Commanding, Idle, WaitingForBluetooth }

    /**
     * Latest per-VIN snapshot of the FSM. [lastAction] / [lastActionMs] are sticky so the
     * UI can show "last unlocked 30s ago" even after the EMA has moved on.
     */
    data class LiveState(
        val ema: Double? = null,
        val lastRssi: Int? = null,
        val fsmState: ProximityFsm.State = ProximityFsm.State.Far,
        val lastSeenMs: Long? = null,
        val lastAction: ProximityFsm.Action? = null,
        val lastActionMs: Long? = null,
    )

    private val _configs = MutableStateFlow<Map<String, ProximityConfig>>(emptyMap())
    val configs: StateFlow<Map<String, ProximityConfig>> = _configs.asStateFlow()

    private val _live = MutableStateFlow<Map<String, LiveState>>(emptyMap())
    val live: StateFlow<Map<String, LiveState>> = _live.asStateFlow()

    private val _serviceState = MutableStateFlow(ServiceState.Stopped)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    /**
     * VIN whose live signal/threshold is shown in the foreground-service notification. Null
     * means "no explicit favorite — fall back to first enabled VIN".
     */
    private val _favoriteVin = MutableStateFlow<String?>(null)
    val favoriteVin: StateFlow<String?> = _favoriteVin.asStateFlow()

    /**
     * Read [CarStore] for currently-enabled cars and either start the foreground service
     * (sending it a reload tickle) or stop it if no cars are enabled. Safe to call from any
     * thread; the actual service start/stop is dispatched through the OS.
     */
    fun refresh(ctx: Context) {
        val store = CarStore(ctx)
        val cfgs = store.enabledProximity()
        _configs.value = cfgs
        _favoriteVin.value = store.favoriteProximityVin()
        val intent = Intent(ctx, ProximityService::class.java)
        if (cfgs.isNotEmpty()) {
            ContextCompat.startForegroundService(ctx, intent)
        } else {
            ctx.stopService(intent)
        }
    }

    /**
     * Persist [vin] (or null to clear) as the favorite and publish to the live flow so the
     * UI star + notification flip in lock-step. Pass null to revert to default-pick behavior.
     */
    fun setFavorite(ctx: Context, vin: String?) {
        CarStore(ctx).setFavoriteProximityVin(vin)
        _favoriteVin.value = vin
    }

    internal fun setConfigs(cfgs: Map<String, ProximityConfig>) {
        _configs.value = cfgs
    }

    /**
     * Seed the in-memory favorite from prefs without re-writing them. Used when the service
     * boots in a fresh process (e.g. START_STICKY restart) and the UI hasn't called
     * [refresh] yet to seed the flow.
     */
    internal fun seedFavoriteVin(vin: String?) {
        _favoriteVin.value = vin
    }

    internal fun publishLive(vin: String, snapshot: LiveState) {
        _live.update { current ->
            val prev = current[vin]
            val merged = if (snapshot.lastAction == null && prev != null) {
                snapshot.copy(lastAction = prev.lastAction, lastActionMs = prev.lastActionMs)
            } else {
                snapshot
            }
            current + (vin to merged)
        }
    }

    internal fun pruneLive(keepVins: Set<String>) {
        _live.update { it.filterKeys { vin -> vin in keepVins } }
    }

    internal fun setServiceState(s: ServiceState) {
        _serviceState.value = s
    }

    internal fun clearOnStop() {
        _serviceState.value = ServiceState.Stopped
    }
}
