package com.tkey.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.vcsec.Vcsec
import com.tkey.ble.CarConnection
import com.tkey.crypto.Identity
import com.tkey.session.TeslaSession
import com.tkey.ui.theme.Accent
import com.tkey.ui.theme.AccentDim
import com.tkey.ui.theme.Danger
import com.tkey.ui.theme.Graphite
import com.tkey.ui.theme.GraphiteHi
import com.tkey.ui.theme.Hairline
import com.tkey.ui.theme.Ink
import com.tkey.ui.theme.Success
import com.tkey.ui.theme.TKeyTheme
import com.tkey.ui.theme.TextMuted
import com.tkey.ui.theme.TextSecondary
import com.tkey.ui.theme.Warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            TKeyTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0),
                ) { _ ->
                    Box(Modifier.fillMaxSize().background(BackgroundGradient())) {
                        Screen(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundGradient(): Brush = Brush.verticalGradient(
    0f to Color(0xFF0B0D11),
    0.45f to Ink,
    1f to Ink,
)

@Composable
private fun Screen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val carStore = remember { CarStore(ctx) }
    val cars = remember { mutableStateListOf<SavedCar>().apply { addAll(carStore.list()) } }
    val identity = remember { Identity.loadOrCreate() }
    val controller = remember { CarController(ctx, identity) }

    var vin by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    val phase = controller.phase.collectAsState().value
    val connection = controller.connection.collectAsState().value
    val session = controller.session.collectAsState().value
    val connState = connection?.state?.collectAsState()?.value
    val sessionStatus = session?.status?.collectAsState()?.value
    val enrollment = session?.enrollment?.collectAsState()?.value
    val rxCount = session?.rxCount?.collectAsState()?.value ?: 0
    val vehicleStatus = session?.vehicleStatus?.collectAsState()?.value
    val vehicleData = session?.vehicleData?.collectAsState()?.value
    val active = phase !is CarController.Phase.Idle
    val coScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            statusError = null
            if (vin.length == 17) controller.start(vin)
        } else {
            statusError = "Permission denied: ${grants.filterValues { !it }.keys.joinToString()}"
        }
    }

    fun startWithPermissions() {
        if (vin.length != 17) return
        val needed = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val missing = needed.filter {
            ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            statusError = null
            controller.start(vin)
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }

    LaunchedEffect(Unit) {
        val lastVin = carStore.lastVin() ?: return@LaunchedEffect
        val car = cars.firstOrNull { it.vin == lastVin } ?: return@LaunchedEffect
        vin = car.vin
        selectedName = car.name
        startWithPermissions()
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (active) {
            BackPill(label = "Vehicles", onClick = {
                controller.stop()
                vin = ""
                selectedName = null
                statusError = null
            })
            HeroCard(
                name = selectedName ?: "Tesla",
                vin = vin,
                phase = phase,
                connState = connState,
                vehicleStatus = vehicleStatus,
                vehicleData = vehicleData,
                enrollment = enrollment,
                refreshEnabled = connState is CarConnection.State.Ready && session != null,
                onRefresh = {
                    session?.let { sess ->
                        coScope.launch {
                            runCatching { sess.requestVehicleStatus() }
                                .onFailure { statusError = it.message }
                            runCatching { sess.requestVehicleData() }
                                .onFailure { statusError = it.message }
                        }
                    }
                },
            )

            session?.let { sess ->
                val sessionReady = sessionStatus is TeslaSession.Status.Established &&
                    sessionStatus.statusEnum == com.tesla.generated.signatures.Signatures.Session_Info_Status.SESSION_INFO_STATUS_OK
                val connReady = connState is CarConnection.State.Ready

                if (enrollment is TeslaSession.Enrollment.AwaitingKeycard) {
                    KeycardPrompt()
                }

                if (!sessionReady) {
                    HandshakeRow(
                        connReady = connReady,
                        sessionStatus = sessionStatus,
                        enrollment = enrollment,
                        onSession = {
                            coScope.launch {
                                runCatching {
                                    sess.requestSessionInfo(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY)
                                }.onFailure { statusError = it.message }
                            }
                        },
                        onEnroll = {
                            coScope.launch {
                                runCatching { sess.requestEnrollment() }
                                    .onFailure { statusError = it.message }
                            }
                        },
                    )
                }

                val infotainmentReady = sess.isReady(UniversalMessage.Domain.DOMAIN_INFOTAINMENT)
                ActionGrid(
                    enabled = sessionReady,
                    infotainmentEnabled = sessionReady && infotainmentReady,
                    onLock = { coScope.launch { runCatching { sess.lock() }.onFailure { statusError = it.message } } },
                    onUnlock = { coScope.launch { runCatching { sess.unlock() }.onFailure { statusError = it.message } } },
                    onTrunkOpen = { coScope.launch { runCatching { sess.openTrunk() }.onFailure { statusError = it.message } } },
                    onTrunkClose = { coScope.launch { runCatching { sess.closeTrunk() }.onFailure { statusError = it.message } } },
                    onPortOpen = { coScope.launch { runCatching { sess.openChargePort() }.onFailure { statusError = it.message } } },
                    onPortClose = { coScope.launch { runCatching { sess.closeChargePort() }.onFailure { statusError = it.message } } },
                    onVentWindows = { coScope.launch { runCatching { sess.ventWindows() }.onFailure { statusError = it.message } } },
                    onCloseWindows = { coScope.launch { runCatching { sess.closeWindows() }.onFailure { statusError = it.message } } },
                    onVolumeDown = { coScope.launch { runCatching { sess.bumpVolume(-1) }.onFailure { statusError = it.message } } },
                    onVolumeUp = { coScope.launch { runCatching { sess.bumpVolume(1) }.onFailure { statusError = it.message } } },
                )

                VehicleStatusCard(vehicleStatus)

                TechnicalDetails(
                    expanded = showDetails,
                    onToggle = { showDetails = !showDetails },
                    phase = phase,
                    connState = connState,
                    sessionStatus = sessionStatus,
                    enrollment = enrollment,
                    rxCount = rxCount,
                    identity = identity,
                )
            }

            statusError?.let { ErrorBanner(it) }
        } else {
            statusError?.let { ErrorBanner(it) }

            SectionLabel(
                title = "Your vehicles",
                trailing = if (cars.isEmpty()) null else "${cars.size}/${CarStore.MAX_CARS}",
            )
            if (cars.isEmpty()) {
                EmptyCarsCard(onAdd = { showAddDialog = true })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (car in cars) {
                        CarCard(
                            car = car,
                            onSelect = {
                                vin = car.vin
                                selectedName = car.name
                                statusError = null
                                carStore.setLastVin(car.vin)
                                startWithPermissions()
                            },
                            onDelete = {
                                val updated = carStore.remove(car.vin)
                                cars.clear()
                                cars.addAll(updated)
                                if (vin == car.vin) {
                                    vin = ""
                                    selectedName = null
                                }
                            },
                        )
                    }
                    if (cars.size < CarStore.MAX_CARS) {
                        AddCarTile(onClick = { showAddDialog = true })
                    }
                }
            }
        }

        if (showAddDialog) {
            AddCarDialog(
                onDismiss = { showAddDialog = false },
                onSave = { name, newVin ->
                    val updated = carStore.add(SavedCar(name = name, vin = newVin))
                    if (updated == null) {
                        statusError = "Car list is full (${CarStore.MAX_CARS} max)."
                    } else {
                        cars.clear()
                        cars.addAll(updated)
                        vin = newVin
                        selectedName = name
                        statusError = null
                    }
                    showAddDialog = false
                },
            )
        }
    }
}

// region — Section label + back pill

@Composable
private fun BackPill(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(GraphiteHi)
            .border(1.dp, Hairline, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SectionLabel(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
            color = TextMuted,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// endregion

// region — Idle: car list

@Composable
private fun EmptyCarsCard(onAdd: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onAdd),
        color = Graphite,
    ) {
        Column(
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(GraphiteHi),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = Accent,
                )
            }
            Text(
                "No vehicles yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Add your Tesla to start using your phone as a key. You'll need the 17-character VIN from the door jamb or your car settings.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Ink,
                ),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add vehicle", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CarCard(
    car: SavedCar,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect),
        color = Graphite,
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1B3631), AccentDim),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ElectricCar,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    car.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "VIN",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = TextMuted,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "••••••${car.vin.takeLast(6)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            IconAffordance(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                tint = Accent,
                bg = AccentDim,
                onClick = onSelect,
            )
            Spacer(Modifier.width(8.dp))
            IconAffordance(
                icon = Icons.Filled.Delete,
                tint = TextMuted,
                bg = GraphiteHi,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun IconAffordance(
    icon: ImageVector,
    tint: Color,
    bg: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AddCarTile(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GraphiteHi),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "Add another vehicle",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        }
    }
}

// endregion

// region — Active: hero card

@Composable
private fun HeroCard(
    name: String,
    vin: String,
    phase: CarController.Phase,
    connState: CarConnection.State?,
    vehicleStatus: TeslaSession.VehicleStatusSnapshot?,
    vehicleData: TeslaSession.VehicleDataSnapshot?,
    enrollment: TeslaSession.Enrollment?,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean,
) {
    val isLocked = vehicleStatus?.status?.vehicleLockState ==
        Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED
    val hasStatus = vehicleStatus != null
    val ready = phase is CarController.Phase.Ready

    val ringColor by animateColorAsState(
        targetValue = when {
            phase is CarController.Phase.Reconnecting -> Danger
            phase is CarController.Phase.Idle -> TextMuted
            !ready -> Warning
            !hasStatus -> Accent
            isLocked -> Success
            else -> Warning
        },
        animationSpec = tween(durationMillis = 600),
        label = "ring-color",
    )

    val stateLabel = when {
        phase is CarController.Phase.Reconnecting -> "RECONNECTING"
        phase is CarController.Phase.Scanning -> "SCANNING"
        phase is CarController.Phase.Connecting -> "CONNECTING"
        phase is CarController.Phase.Handshaking -> "HANDSHAKING"
        enrollment is TeslaSession.Enrollment.AwaitingKeycard -> "TAP KEYCARD"
        !ready -> "STARTING"
        !hasStatus -> "AWAITING STATUS"
        isLocked -> "LOCKED"
        else -> "UNLOCKED"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Graphite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        if (vin.isNotEmpty()) "VIN ••••••${vin.takeLast(6)}" else "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    PhaseChip(phase = phase, connState = connState)
                    val cs = vehicleData?.data?.chargeState
                    if (cs != null && (cs.hasBatteryRange() || cs.hasEstBatteryRange())) {
                        Spacer(Modifier.height(6.dp))
                        val miles = if (cs.hasEstBatteryRange()) cs.estBatteryRange else cs.batteryRange
                        val pct = if (cs.hasBatteryLevel()) cs.batteryLevel else null
                        RangeChip(miles = miles, batteryPct = pct)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LockOrb(color = ringColor, locked = isLocked && ready && hasStatus, animate = !ready)

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stateLabel,
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.8.sp),
                            color = ringColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        InlineRefreshChip(enabled = refreshEnabled, onClick = onRefresh)
                    }
                    Spacer(Modifier.height(4.dp))
                    val now = rememberNowMs()
                    val sub = when (phase) {
                        is CarController.Phase.Scanning ->
                            "Looking for the car. Tap a door handle to wake it if asleep."
                        is CarController.Phase.Connecting -> "Establishing BLE link…"
                        is CarController.Phase.Handshaking -> "Negotiating encrypted session…"
                        is CarController.Phase.Reconnecting ->
                            "Retry in ${(phase.remainingMs + 999) / 1000}s · ${phase.reason}"
                        is CarController.Phase.Ready ->
                            if (hasStatus) {
                                val received = vehicleStatus!!.receivedAtMs
                                val ageSec = ((now - received) / 1000).coerceAtLeast(0)
                                "Updated ${formatLocalTime(received)} · ${ageSec}s ago"
                            } else "Waiting for vehicle status…"
                        else -> ""
                    }
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockOrb(color: Color, locked: Boolean, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = if (animate) 0.55f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .size(82.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.18f), Color.Transparent),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.10f))
                .border(1.dp, color.copy(alpha = if (animate) pulse * 0.7f else 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GraphiteHi)
                    .border(1.dp, color.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun RangeChip(miles: Float, batteryPct: Int?) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(GraphiteHi)
            .border(1.dp, Hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.BatteryChargingFull,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        val rangeText = "${miles.toInt()} mi"
        val pctText = batteryPct?.let { " · $it%" } ?: ""
        Text(
            rangeText + pctText,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.SansSerif,
        )
    }
}

@Composable
private fun InlineRefreshChip(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(GraphiteHi)
            .border(1.dp, Hairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "Refresh status",
            tint = Accent,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun PhaseChip(phase: CarController.Phase, connState: CarConnection.State?) {
    val ready = phase is CarController.Phase.Ready
    val animating = phase is CarController.Phase.Scanning ||
        phase is CarController.Phase.Connecting ||
        phase is CarController.Phase.Handshaking
    val color = when {
        phase is CarController.Phase.Reconnecting -> Danger
        ready -> Success
        animating -> Accent
        else -> TextMuted
    }
    val icon = when {
        phase is CarController.Phase.Scanning -> Icons.AutoMirrored.Filled.BluetoothSearching
        ready -> Icons.Filled.BluetoothConnected
        phase is CarController.Phase.Reconnecting -> Icons.Filled.Bluetooth
        else -> Icons.Filled.Sync
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(GraphiteHi)
            .border(1.dp, Hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(color = color, animate = animating)
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            connStateLabel(connState, ready),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.SansSerif,
        )
    }
}

private fun connStateLabel(state: CarConnection.State?, ready: Boolean): String = when {
    ready && state is CarConnection.State.Ready -> "LINK · MTU ${state.mtu}"
    state is CarConnection.State.Connecting -> "GATT"
    state is CarConnection.State.DiscoveringServices -> "SVC"
    state is CarConnection.State.EnablingNotifications -> "NTFY"
    state is CarConnection.State.Ready -> "LINK · MTU ${state.mtu}"
    state is CarConnection.State.Failed -> "FAILED"
    state is CarConnection.State.Disconnected -> "DOWN"
    else -> "—"
}

@Composable
private fun PulseDot(color: Color, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = if (animate) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot-alpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (animate) alpha else 1f)),
    )
}

// endregion

// region — Active: keycard prompt + handshake

@Composable
private fun KeycardPrompt() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x33FBBF24),
        border = androidx.compose.foundation.BorderStroke(1.dp, Warning.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Warning.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = Warning)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tap keycard now",
                    style = MaterialTheme.typography.titleMedium,
                    color = Warning,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Hold your Tesla keycard on the center console to authorize this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun HandshakeRow(
    connReady: Boolean,
    sessionStatus: TeslaSession.Status?,
    enrollment: TeslaSession.Enrollment?,
    onSession: () -> Unit,
    onEnroll: () -> Unit,
) {
    SectionLabel("Setup")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Graphite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = "Session",
                    icon = Icons.Filled.Sync,
                    enabled = connReady,
                    onClick = onSession,
                )
                SecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = "Enroll",
                    icon = Icons.Filled.Key,
                    enabled = connReady,
                    onClick = onEnroll,
                )
            }
            val sub = when {
                enrollment is TeslaSession.Enrollment.AwaitingKeycard -> "Awaiting keycard tap…"
                enrollment is TeslaSession.Enrollment.Failed -> "Enrollment failed: ${enrollment.reason}"
                sessionStatus is TeslaSession.Status.Failed -> "Session failed: ${sessionStatus.reason}"
                sessionStatus is TeslaSession.Status.Requested -> "Requesting session info…"
                else -> "Hit Session to start the handshake, then Enroll for first-time pairing."
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

// endregion

// region — Action grid

@Composable
private fun ActionGrid(
    enabled: Boolean,
    infotainmentEnabled: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onTrunkOpen: () -> Unit,
    onTrunkClose: () -> Unit,
    onPortOpen: () -> Unit,
    onPortClose: () -> Unit,
    onVentWindows: () -> Unit,
    onCloseWindows: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
) {
    SectionLabel("Controls")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Lock,
                label = "Lock",
                enabled = enabled,
                accent = Success,
                onClick = onLock,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LockOpen,
                label = "Unlock",
                enabled = enabled,
                accent = Warning,
                onClick = onUnlock,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.KeyboardArrowUp,
                label = "Trunk ↑",
                enabled = enabled,
                onClick = onTrunkOpen,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.KeyboardArrowDown,
                label = "Trunk ↓",
                enabled = enabled,
                onClick = onTrunkClose,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Power,
                label = "Port ↑",
                enabled = enabled,
                onClick = onPortOpen,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Power,
                label = "Port ↓",
                enabled = enabled,
                onClick = onPortClose,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Air,
                label = "Vent windows",
                enabled = infotainmentEnabled,
                onClick = onVentWindows,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Close,
                label = "Close windows",
                enabled = infotainmentEnabled,
                onClick = onCloseWindows,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.VolumeDown,
                label = "Volume −",
                enabled = infotainmentEnabled,
                onClick = onVolumeDown,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = "Volume +",
                enabled = infotainmentEnabled,
                onClick = onVolumeUp,
            )
        }
    }
}

@Composable
private fun ActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    accent: Color = Accent,
    onClick: () -> Unit,
) {
    val tint = if (enabled) accent else TextMuted
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Graphite)
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun SecondaryButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GraphiteHi)
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// endregion

// region — Vehicle status card

@Composable
private fun VehicleStatusCard(snapshot: TeslaSession.VehicleStatusSnapshot?) {
    SectionLabel("Vehicle status")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Graphite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        if (snapshot == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Waiting for VCSEC status…",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        } else {
            val s = snapshot.status
            val closures = s.closureStatuses
            val tonneauPct = s.detailedClosureStatus.tonneauPercentOpen
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusRow("Lock", lockLabel(s.vehicleLockState), tone = lockTone(s.vehicleLockState))
                StatusRow("Sleep", sleepLabel(s.vehicleSleepStatus), tone = sleepTone(s.vehicleSleepStatus))
                StatusRow("User presence", userLabel(s.userPresence), tone = userTone(s.userPresence))
                Divider()
                StatusRow("Driver door", closureLabel(closures.frontDriverDoor), tone = closureTone(closures.frontDriverDoor))
                StatusRow("Passenger door", closureLabel(closures.frontPassengerDoor), tone = closureTone(closures.frontPassengerDoor))
                StatusRow("Rear driver", closureLabel(closures.rearDriverDoor), tone = closureTone(closures.rearDriverDoor))
                StatusRow("Rear passenger", closureLabel(closures.rearPassengerDoor), tone = closureTone(closures.rearPassengerDoor))
                Divider()
                StatusRow("Frunk", closureLabel(closures.frontTrunk), tone = closureTone(closures.frontTrunk))
                StatusRow("Trunk", closureLabel(closures.rearTrunk), tone = closureTone(closures.rearTrunk))
                StatusRow("Charge port", closureLabel(closures.chargePort), tone = closureTone(closures.chargePort))
                if (tonneauPct > 0 || closures.tonneau != Vcsec.ClosureState_E.CLOSURESTATE_CLOSED) {
                    StatusRow(
                        "Tonneau",
                        closureLabel(closures.tonneau) +
                            if (tonneauPct > 0) "  ($tonneauPct%)" else "",
                        tone = closureTone(closures.tonneau),
                    )
                }
            }
        }
    }
}

private enum class Tone { Good, Warn, Bad, Neutral }

@Composable
private fun StatusRow(label: String, value: String, tone: Tone) {
    val color = when (tone) {
        Tone.Good -> Success
        Tone.Warn -> Warning
        Tone.Bad -> Danger
        Tone.Neutral -> TextSecondary
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline.copy(alpha = 0.6f))
            .padding(vertical = 2.dp),
    )
}

// endregion

// region — Technical details (collapsible)

@Composable
private fun TechnicalDetails(
    expanded: Boolean,
    onToggle: () -> Unit,
    phase: CarController.Phase,
    connState: CarConnection.State?,
    sessionStatus: TeslaSession.Status?,
    enrollment: TeslaSession.Enrollment?,
    rxCount: Int,
    identity: Identity,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Graphite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Diagnostics",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val identityHex = runCatching {
                        identity.publicKeyBytes().joinToString("") { "%02x".format(it) }
                    }.getOrDefault("?")
                    val hw = runCatching { identity.isHardwareBacked }.getOrDefault(false)
                    DiagLine("phase", phaseLabel(phase))
                    DiagLine("transport", connState?.toString() ?: "—")
                    DiagLine("session", sessionStatus?.let { sessionStatusLabel(it) } ?: "Idle")
                    DiagLine("enrollment", enrollment?.let { enrollmentLabel(it) } ?: "Idle")
                    DiagLine("rx parsed", "$rxCount")
                    DiagLine("identity", "${identityHex.take(20)}…")
                    DiagLine("hw-backed", if (hw) "yes" else "no")
                }
            }
        }
    }
}

@Composable
private fun DiagLine(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            key,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// endregion

// region — Error banner

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Danger.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Danger.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Danger, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = Danger,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// endregion

// region — Add car dialog

@Composable
private fun AddCarDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, vin: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var vinInput by rememberSaveable { mutableStateOf("") }
    val nameOk = name.isNotBlank()
    val vinOk = vinInput.length == 17

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Graphite,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Add vehicle",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkTextFieldColors(),
                )
                OutlinedTextField(
                    value = vinInput,
                    onValueChange = { input ->
                        vinInput = input.uppercase().filter { it.isLetterOrDigit() }.take(17)
                    },
                    label = { Text("VIN (17 characters)") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkTextFieldColors(),
                    supportingText = {
                        Text(
                            if (vinOk) "Looks good" else "${vinInput.length}/17",
                            color = if (vinOk) Success else TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = nameOk && vinOk,
                onClick = { onSave(name.trim(), vinInput) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Ink,
                    disabledContainerColor = Hairline,
                    disabledContentColor = TextMuted,
                ),
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Hairline,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextMuted,
    cursorColor = Accent,
    focusedContainerColor = Ink,
    unfocusedContainerColor = Ink,
)

// endregion

// region — Time helpers

@Composable
private fun rememberNowMs(intervalMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private val LocalTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatLocalTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(LocalTimeFormatter)

// endregion

// region — Label helpers

private fun phaseLabel(phase: CarController.Phase): String = when (phase) {
    is CarController.Phase.Idle -> "Idle"
    is CarController.Phase.Scanning -> "Scanning (attempt ${phase.attempt})"
    is CarController.Phase.Connecting -> "Connecting"
    is CarController.Phase.Handshaking -> "Handshaking"
    is CarController.Phase.Ready -> "Ready"
    is CarController.Phase.Reconnecting ->
        "Retry in ${(phase.remainingMs + 999) / 1000}s · attempt ${phase.attempt}: ${phase.reason}"
}

private fun sessionStatusLabel(s: TeslaSession.Status): String = when (s) {
    is TeslaSession.Status.Idle -> "Idle"
    is TeslaSession.Status.Requested -> "Requested ${s.domain.name}"
    is TeslaSession.Status.Established -> "${s.domain.name} · ${s.statusEnum.name} · ctr=${s.counter}"
    is TeslaSession.Status.Failed -> "Failed: ${s.reason}"
}

private fun enrollmentLabel(e: TeslaSession.Enrollment): String = when (e) {
    is TeslaSession.Enrollment.Idle -> "Idle"
    is TeslaSession.Enrollment.Requested -> "Requested"
    is TeslaSession.Enrollment.AwaitingKeycard -> "Awaiting keycard"
    is TeslaSession.Enrollment.Success -> "Success"
    is TeslaSession.Enrollment.Failed -> "Failed: ${e.reason}"
}

private fun lockLabel(v: Vcsec.VehicleLockState_E): String = when (v) {
    Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_UNLOCKED -> "Unlocked"
    Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED -> "Locked"
    Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_INTERNAL_LOCKED -> "Internal locked"
    Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_SELECTIVE_UNLOCKED -> "Selective unlock"
    else -> v.name
}

private fun lockTone(v: Vcsec.VehicleLockState_E): Tone = when (v) {
    Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED -> Tone.Good
    Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_INTERNAL_LOCKED -> Tone.Good
    Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_UNLOCKED -> Tone.Warn
    Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_SELECTIVE_UNLOCKED -> Tone.Warn
    else -> Tone.Neutral
}

private fun sleepLabel(v: Vcsec.VehicleSleepStatus_E): String = when (v) {
    Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_AWAKE -> "Awake"
    Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP -> "Asleep"
    Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_UNKNOWN -> "Unknown"
    else -> v.name
}

private fun sleepTone(v: Vcsec.VehicleSleepStatus_E): Tone = when (v) {
    Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_AWAKE -> Tone.Good
    Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP -> Tone.Neutral
    else -> Tone.Neutral
}

private fun userLabel(v: Vcsec.UserPresence_E): String = when (v) {
    Vcsec.UserPresence_E.VEHICLE_USER_PRESENCE_PRESENT -> "Present"
    Vcsec.UserPresence_E.VEHICLE_USER_PRESENCE_NOT_PRESENT -> "Not present"
    Vcsec.UserPresence_E.VEHICLE_USER_PRESENCE_UNKNOWN -> "Unknown"
    else -> v.name
}

private fun userTone(v: Vcsec.UserPresence_E): Tone = when (v) {
    Vcsec.UserPresence_E.VEHICLE_USER_PRESENCE_PRESENT -> Tone.Good
    else -> Tone.Neutral
}

private fun closureLabel(v: Vcsec.ClosureState_E): String = when (v) {
    Vcsec.ClosureState_E.CLOSURESTATE_CLOSED -> "Closed"
    Vcsec.ClosureState_E.CLOSURESTATE_OPEN -> "Open"
    Vcsec.ClosureState_E.CLOSURESTATE_AJAR -> "Ajar"
    Vcsec.ClosureState_E.CLOSURESTATE_OPENING -> "Opening"
    Vcsec.ClosureState_E.CLOSURESTATE_CLOSING -> "Closing"
    Vcsec.ClosureState_E.CLOSURESTATE_FAILED_UNLATCH -> "Failed unlatch"
    Vcsec.ClosureState_E.CLOSURESTATE_UNKNOWN -> "Unknown"
    else -> v.name
}

private fun closureTone(v: Vcsec.ClosureState_E): Tone = when (v) {
    Vcsec.ClosureState_E.CLOSURESTATE_CLOSED -> Tone.Good
    Vcsec.ClosureState_E.CLOSURESTATE_OPEN -> Tone.Warn
    Vcsec.ClosureState_E.CLOSURESTATE_AJAR -> Tone.Warn
    Vcsec.ClosureState_E.CLOSURESTATE_OPENING -> Tone.Warn
    Vcsec.ClosureState_E.CLOSURESTATE_CLOSING -> Tone.Warn
    Vcsec.ClosureState_E.CLOSURESTATE_FAILED_UNLATCH -> Tone.Bad
    else -> Tone.Neutral
}

// endregion
