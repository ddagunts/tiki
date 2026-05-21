package com.tkey.ui.proximity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.app.Service
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tesla.generated.universalmessage.UniversalMessage
import com.tkey.ble.CarConnection
import com.tkey.ble.CarScanner
import com.tkey.ble.VinHash
import com.tkey.crypto.Identity
import com.tkey.session.TeslaSession
import com.tkey.ui.CarStore
import com.tkey.ui.MainActivity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "TKey.Prox"

/**
 * Foreground service that runs one BLE-discovery loop and routes beacons to a per-VIN
 * [ProximityFsm]. When an FSM emits an Unlock/Lock action, the service pauses scanning,
 * opens an ephemeral [CarConnection] + [TeslaSession], dispatches the RKE command, and
 * resumes scanning.
 *
 * Auto-stop: while neither a beacon nor a significant-motion event has been seen for
 * [IDLE_AFTER_MS], discovery is paused and the service waits on Android's
 * `TYPE_SIGNIFICANT_MOTION` trigger sensor for the phone to start moving again.
 *
 * Lifecycle: started via [ProximityRegistry.refresh] when any car has proximity enabled.
 * Self-stops when [CarStore.enabledProximity] returns empty. Survives screen-off and app
 * being swiped away (foreground type `connectedDevice`).
 */
class ProximityService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Concurrent: reload() (onStartCommand thread) mutates while scannerLoop / tickerLoop
    // (Default dispatcher coroutines) read. Plain HashMap can NPE / loop under structural mods.
    private val fsms = ConcurrentHashMap<String, ProximityFsm>()
    private val targets = ConcurrentHashMap<String, String>() // VinHash localName -> VIN
    // Bounded so a stuck commander can't backlog stale actions; drop the oldest
    // (lock/unlock decisions are intentionally short-lived and rapidly re-derived).
    private val actionQueue = Channel<Pair<String, ProximityFsm.Action>>(
        capacity = 8,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    private val identity by lazy { Identity.loadOrCreate() }

    private var scannerJob: Job? = null
    private var tickerJob: Job? = null
    private var commanderStarted = false
    private var idleWatcherStarted = false
    private var inCommand = false

    private val lastBeaconAtMs = AtomicLong(0L)
    private val lastMotionAtMs = AtomicLong(System.currentTimeMillis())

    @Volatile private var btEnabled = true
    private var btReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        registerBluetoothStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIF_ID,
            buildNotification("Watching for vehicles"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        reload()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        btReceiver?.let { runCatching { unregisterReceiver(it) } }
        btReceiver = null
        ProximityRegistry.clearOnStop()
        super.onDestroy()
    }

    private fun registerBluetoothStateReceiver() {
        btEnabled = getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.STATE_OFF,
                )
                when (state) {
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        if (btEnabled) {
                            btEnabled = false
                            Log.i(TAG, "Bluetooth disabled; pausing scanner")
                            scope.launch { pauseForBluetooth() }
                        }
                    }
                    BluetoothAdapter.STATE_ON -> {
                        if (!btEnabled) {
                            btEnabled = true
                            Log.i(TAG, "Bluetooth re-enabled; resuming scanner")
                            // Reset the motion timer so the idle watcher gives the FSM a
                            // chance to see beacons before declaring idle.
                            lastMotionAtMs.set(System.currentTimeMillis())
                            ensureLoopsRunning()
                        }
                    }
                }
            }
        }
        // RECEIVER_EXPORTED is required: ACTION_STATE_CHANGED is dispatched from the
        // com.android.bluetooth process (not system_server on most devices), so
        // NOT_EXPORTED can silently drop it. Matches the CarScanner receiver flag.
        registerReceiver(
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            Context.RECEIVER_EXPORTED,
        )
        btReceiver = receiver
    }

    private fun reload() {
        val cfgs = CarStore(this).enabledProximity()
        ProximityRegistry.setConfigs(cfgs)
        ProximityRegistry.pruneLive(cfgs.keys)
        if (cfgs.isEmpty()) {
            stopSelf()
            return
        }
        // Drop FSMs for cars that disappeared.
        (fsms.keys - cfgs.keys).forEach { fsms.remove(it) }
        // Add new FSMs for newly-enabled cars; nudge in-place for existing ones so that
        // live EMA / timers don't get blown away every time a slider is dragged.
        for ((vin, cfg) in cfgs) {
            val existing = fsms[vin]
            if (existing == null) {
                fsms[vin] = ProximityFsm(cfg).also {
                    ProximityRegistry.publishLive(
                        vin,
                        ProximityRegistry.LiveState(fsmState = it.currentState),
                    )
                }
            } else {
                existing.updateConfig(cfg)
            }
        }
        targets.clear()
        for (vin in cfgs.keys) targets[VinHash.localName(vin)] = vin

        ensureLoopsRunning()
    }

    private fun ensureLoopsRunning() {
        if (btEnabled) {
            if (scannerJob == null && !inCommand) {
                scannerJob = scope.launch { scannerLoop() }
                ProximityRegistry.setServiceState(ProximityRegistry.ServiceState.Scanning)
            }
        } else {
            ProximityRegistry.setServiceState(ProximityRegistry.ServiceState.WaitingForBluetooth)
            updateNotification("Waiting for Bluetooth")
        }
        if (tickerJob == null) {
            tickerJob = scope.launch { tickerLoop() }
        }
        if (!commanderStarted) {
            commanderStarted = true
            scope.launch { commanderLoop() }
        }
        if (!idleWatcherStarted) {
            idleWatcherStarted = true
            scope.launch { idleWatcherLoop() }
        }
    }

    private suspend fun scannerLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                CarScanner(this@ProximityService).discoverRaw().collect { beacon ->
                    val now = System.currentTimeMillis()
                    lastBeaconAtMs.set(now)
                    val vin = targets[beacon.localName] ?: return@collect
                    val fsm = fsms[vin] ?: return@collect
                    val action = fsm.feed(beacon.rssi, now)
                    ProximityRegistry.publishLive(
                        vin,
                        ProximityRegistry.LiveState(
                            ema = fsm.emaRssi,
                            lastRssi = beacon.rssi,
                            fsmState = fsm.currentState,
                            lastSeenMs = now,
                            lastAction = action,
                            lastActionMs = if (action != null) now else null,
                        ),
                    )
                    if (action != null) actionQueue.send(vin to action)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!btEnabled) {
                    Log.i(TAG, "scanner exiting: bluetooth off")
                    return
                }
                Log.w(TAG, "scanner error: ${e.message}; restarting in 3s")
                delay(3_000)
            }
        }
    }

    private suspend fun tickerLoop() {
        while (currentCoroutineContext().isActive) {
            delay(TICK_MS)
            val now = System.currentTimeMillis()
            for ((vin, fsm) in fsms.toMap()) {
                val action = fsm.tick(now)
                ProximityRegistry.publishLive(
                    vin,
                    ProximityRegistry.LiveState(
                        ema = fsm.emaRssi,
                        lastRssi = null,
                        fsmState = fsm.currentState,
                        lastSeenMs = if (fsm.lastSeenMs > Long.MIN_VALUE / 2) fsm.lastSeenMs else null,
                        lastAction = action,
                        lastActionMs = if (action != null) now else null,
                    ),
                )
                if (action != null) actionQueue.send(vin to action)
            }
        }
    }

    private suspend fun commanderLoop() {
        for ((vin, action) in actionQueue) {
            inCommand = true
            scannerJob?.let { prev ->
                scannerJob = null
                runCatching { prev.cancelAndJoin() }
            }
            ProximityRegistry.setServiceState(ProximityRegistry.ServiceState.Commanding)
            updateNotification(actionLabel(action))
            try {
                performAction(vin, action)
                Log.i(TAG, "$action $vin OK")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "$action $vin failed: ${e.message}")
            }
            inCommand = false
            if (fsms.isNotEmpty() && scannerJob == null && btEnabled) {
                scannerJob = scope.launch { scannerLoop() }
                ProximityRegistry.setServiceState(ProximityRegistry.ServiceState.Scanning)
                updateNotification("Watching for vehicles")
            } else if (!btEnabled) {
                ProximityRegistry.setServiceState(ProximityRegistry.ServiceState.WaitingForBluetooth)
                updateNotification("Waiting for Bluetooth")
            }
        }
    }

    /** Connect → handshake → send RKE → disconnect. Throws on any failure. */
    private suspend fun performAction(vin: String, action: ProximityFsm.Action) {
        val match = withTimeoutOrNull(BEACON_TIMEOUT_MS) {
            CarScanner(this@ProximityService).discover(vin)
                .mapNotNull { (it as? CarScanner.Event.Match)?.beacon }
                .first()
        } ?: error("no beacon within ${BEACON_TIMEOUT_MS / 1000}s")

        val conn = CarConnection.fromMac(this@ProximityService, match.address)
        try {
            conn.connect()
            val transport = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                conn.state.first {
                    it is CarConnection.State.Ready ||
                        it is CarConnection.State.Failed ||
                        it is CarConnection.State.Disconnected
                }
            } ?: error("transport timeout")
            if (transport !is CarConnection.State.Ready) error("transport ended in $transport")

            val session = TeslaSession(identity, conn, vin)
            try {
                session.start()
                session.requestSessionInfo(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY)
                val outcome = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) {
                    session.status.first {
                        it is TeslaSession.Status.Established || it is TeslaSession.Status.Failed
                    }
                } ?: error("handshake timeout")
                if (outcome is TeslaSession.Status.Failed) error("handshake: ${outcome.reason}")

                when (action) {
                    ProximityFsm.Action.Unlock -> session.unlock()
                    ProximityFsm.Action.Lock -> session.lock()
                }
            } finally {
                session.stop()
            }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun idleWatcherLoop() {
        while (currentCoroutineContext().isActive) {
            delay(60_000)
            if (inCommand || fsms.isEmpty() || !btEnabled) continue
            val now = System.currentTimeMillis()
            val beaconAge = now - lastBeaconAtMs.get()
            val motionAge = now - lastMotionAtMs.get()
            val hasEverSeenBeacon = lastBeaconAtMs.get() > 0
            // Wait until we've had at least one beacon (calibration grace) before considering idle.
            if (hasEverSeenBeacon && beaconAge > IDLE_AFTER_MS && motionAge > IDLE_AFTER_MS) {
                pauseForMotion()
            }
        }
    }

    private suspend fun pauseForBluetooth() {
        scannerJob?.let { prev ->
            scannerJob = null
            runCatching { prev.cancelAndJoin() }
        }
        ProximityRegistry.setServiceState(ProximityRegistry.ServiceState.WaitingForBluetooth)
        updateNotification("Waiting for Bluetooth")
    }

    private suspend fun pauseForMotion() {
        scannerJob?.let { prev ->
            scannerJob = null
            runCatching { prev.cancelAndJoin() }
        }
        ProximityRegistry.setServiceState(ProximityRegistry.ServiceState.Idle)
        updateNotification("Idle — waiting for motion")
        try {
            awaitSignificantMotion()
            lastMotionAtMs.set(System.currentTimeMillis())
        } finally {
            if (fsms.isNotEmpty() && scannerJob == null && btEnabled) {
                scannerJob = scope.launch { scannerLoop() }
                ProximityRegistry.setServiceState(ProximityRegistry.ServiceState.Scanning)
                updateNotification("Watching for vehicles")
            }
        }
    }

    private suspend fun awaitSignificantMotion(): Unit = suspendCancellableCoroutine { cont ->
        val sm = getSystemService(SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        if (sm == null || sensor == null) {
            // No sensor: fall back to a long timed pause so we don't burn the battery scanning.
            scope.launch {
                delay(15 * 60_000L)
                if (cont.isActive) cont.resumeWith(Result.success(Unit))
            }
            return@suspendCancellableCoroutine
        }
        val listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent) {
                if (cont.isActive) cont.resumeWith(Result.success(Unit))
            }
        }
        val ok = sm.requestTriggerSensor(listener, sensor)
        if (!ok) {
            if (cont.isActive) cont.resumeWith(Result.success(Unit))
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation {
            runCatching { sm.cancelTriggerSensor(listener, sensor) }
        }
    }

    private fun actionLabel(a: ProximityFsm.Action) = when (a) {
        ProximityFsm.Action.Unlock -> "Unlocking…"
        ProximityFsm.Action.Lock -> "Locking…"
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Proximity unlock",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps TKey listening for your car so it can unlock when you approach."
            setShowBadge(false)
        }
        mgr.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("TKey proximity")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "tkey_proximity"
        private const val TICK_MS = 2_000L
        private const val IDLE_AFTER_MS = 10 * 60_000L
        private const val BEACON_TIMEOUT_MS = 30_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val HANDSHAKE_TIMEOUT_MS = 10_000L
    }
}
