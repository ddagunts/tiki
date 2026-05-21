package com.tkey.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
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
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.carserver.vehicle.Vehicle
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.vcsec.Vcsec
import com.tkey.ble.CarConnection
import com.tkey.crypto.Identity
import com.tkey.keycard.KeycardIdentity
import com.tkey.session.TeslaSession
import com.tkey.ui.proximity.ProximityConfig
import com.tkey.ui.proximity.ProximityFsm
import com.tkey.ui.proximity.ProximityRegistry
import com.tkey.ui.theme.Accent
import com.tkey.ui.theme.AccentDim
import com.tkey.ui.theme.Danger
import com.tkey.ui.theme.DangerDim
import com.tkey.ui.theme.Graphite
import com.tkey.ui.theme.GraphiteHi
import com.tkey.ui.theme.Hairline
import com.tkey.ui.theme.Info
import com.tkey.ui.theme.InfoDim
import com.tkey.ui.theme.Ink
import com.tkey.ui.theme.Success
import com.tkey.ui.theme.TKeyTheme
import com.tkey.ui.theme.TextMuted
import com.tkey.ui.theme.TextSecondary
import com.tkey.ui.theme.Warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
    var subscreen by rememberSaveable { mutableStateOf("main") }
    var settingsVin by rememberSaveable { mutableStateOf<String?>(null) }
    val proxConfigs = remember(cars.size) {
        cars.associate { it.vin to carStore.getProximity(it.vin) }
    }

    LaunchedEffect(Unit) {
        // Seed registry so settings UI reflects persisted enabled-state immediately.
        ProximityRegistry.refresh(ctx)
    }

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

    // Flip paired=true when either the user just enrolled successfully, or the car
    // came back with an OK session_info (key was already on the whitelist). The vin
    // is read from the running session so this works whether the controller was
    // started from the car view or from settings.
    LaunchedEffect(enrollment, sessionStatus, session) {
        val sessVin = session?.vin ?: return@LaunchedEffect
        if (sessVin.length != 17 || carStore.isPaired(sessVin)) return@LaunchedEffect
        val enrolled = enrollment is TeslaSession.Enrollment.Success
        val okSession = sessionStatus is TeslaSession.Status.Established &&
            sessionStatus.statusEnum ==
                com.tesla.generated.signatures.Signatures.Session_Info_Status.SESSION_INFO_STATUS_OK
        if (enrolled || okSession) carStore.setPaired(sessVin, true)
    }

    // VIN to start once permissions come back from the system dialog.
    var pendingStartVin by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val target = pendingStartVin
        pendingStartVin = null
        if (grants.values.all { it }) {
            statusError = null
            if (target != null && target.length == 17) {
                controller.start(target) { carStore.isPaired(target) }
            }
        } else {
            statusError = "Permission denied: ${grants.filterValues { !it }.keys.joinToString()}"
        }
    }

    fun startWithPermissions(targetVin: String) {
        if (targetVin.length != 17) return
        // Persist before we attempt anything — Settings-driven auto-start, CarCard tap,
        // and the auto-resume LaunchedEffect all funnel through here, so this is the one
        // place that's guaranteed to fire when the user expresses intent for a specific car.
        carStore.setLastVin(targetVin)
        val needed = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val missing = needed.filter {
            ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            statusError = null
            controller.start(targetVin) { carStore.isPaired(targetVin) }
        } else {
            pendingStartVin = targetVin
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
        startWithPermissions(car.vin)
    }

    // Auto-refresh while a user is in the car: poll vehicle status + data every 60s so the
    // hero card and status panels don't go stale during a drive. Loop only runs when the
    // last status snapshot reported presence; presence flipping away tears the loop down,
    // presence flipping back in starts a new one.
    val userPresent = vehicleStatus?.status?.userPresence ==
        Vcsec.UserPresence_E.VEHICLE_USER_PRESENCE_PRESENT
    LaunchedEffect(session, userPresent) {
        val sess = session ?: return@LaunchedEffect
        if (!userPresent) return@LaunchedEffect
        while (true) {
            delay(60_000)
            runCatching { sess.requestVehicleStatus() }
            if (sess.isReady(UniversalMessage.Domain.DOMAIN_INFOTAINMENT)) {
                runCatching { sess.requestVehicleData() }
            }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (settingsVin != null) {
            val car = cars.firstOrNull { it.vin == settingsVin }
            if (car == null) {
                LaunchedEffect(settingsVin) { settingsVin = null }
            } else {
                ProximitySettingsScreen(
                    car = car,
                    controller = controller,
                    onStart = { startWithPermissions(car.vin) },
                    onBack = {
                        controller.stop()
                        settingsVin = null
                    },
                )
            }
        } else if (active) {
            // Single-car users can opt to hide the back pill — they never need to switch cars,
            // and reclaiming that vertical slice keeps the hero card at the very top.
            val showBackPill = cars.size > 1 || !carStore.hideBackToVehicles()
            if (showBackPill) {
                BackPill(label = "Vehicles", onClick = {
                    controller.stop()
                    vin = ""
                    selectedName = null
                    statusError = null
                    subscreen = "main"
                })
            }
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
                            if (sess.isReady(UniversalMessage.Domain.DOMAIN_INFOTAINMENT)) {
                                runCatching { sess.requestVehicleData() }
                                    .onFailure { statusError = it.message }
                            } else {
                                // Infotainment hasn't woken up yet — re-kick the handshake
                                // instead of throwing. The controller's maintenance loop
                                // will pick up the reply and auto-fire requestVehicleData.
                                runCatching {
                                    sess.requestSessionInfo(UniversalMessage.Domain.DOMAIN_INFOTAINMENT)
                                }.onFailure { statusError = it.message }
                            }
                        }
                    }
                },
            )

            session?.let { sess ->
                val sessionReady = sessionStatus is TeslaSession.Status.Established &&
                    sessionStatus.statusEnum == com.tesla.generated.signatures.Signatures.Session_Info_Status.SESSION_INFO_STATUS_OK

                if (enrollment is TeslaSession.Enrollment.AwaitingKeycard) {
                    KeycardPrompt()
                }

                val infotainmentReady = sess.isReady(UniversalMessage.Domain.DOMAIN_INFOTAINMENT)
                val onErr: (Throwable) -> Unit = { statusError = it.message }

                when (subscreen) {
                    "comfort" -> ComfortScreen(
                        enabled = sessionReady && infotainmentReady,
                        vehicleData = vehicleData,
                        onBack = { subscreen = "main" },
                        run = { block -> coScope.launch { runCatching { block() }.onFailure(onErr) } },
                        session = sess,
                    )
                    "advanced" -> AdvancedScreen(
                        enabled = sessionReady && infotainmentReady,
                        vehicleData = vehicleData,
                        onBack = { subscreen = "main" },
                        run = { block -> coScope.launch { runCatching { block() }.onFailure(onErr) } },
                        session = sess,
                    )
                    else -> {
                        ActionGrid(
                            enabled = sessionReady,
                            infotainmentEnabled = sessionReady && infotainmentReady,
                            onLock = { coScope.launch { runCatching { sess.lock() }.onFailure(onErr) } },
                            onUnlock = { coScope.launch { runCatching { sess.unlock() }.onFailure(onErr) } },
                            onTrunkOpen = { coScope.launch { runCatching { sess.openTrunk() }.onFailure(onErr) } },
                            onTrunkClose = { coScope.launch { runCatching { sess.closeTrunk() }.onFailure(onErr) } },
                            onPortOpen = { coScope.launch { runCatching { sess.openChargePort() }.onFailure(onErr) } },
                            onPortClose = { coScope.launch { runCatching { sess.closeChargePort() }.onFailure(onErr) } },
                            onVentWindows = { coScope.launch { runCatching { sess.ventWindows() }.onFailure(onErr) } },
                            onCloseWindows = { coScope.launch { runCatching { sess.closeWindows() }.onFailure(onErr) } },
                            onComfort = { subscreen = "comfort" },
                            onAdvanced = { subscreen = "advanced" },
                        )

                        ProximityToggleCard(
                            vin = vin,
                            carStore = carStore,
                            onOpenSettings = { settingsVin = vin },
                        )

                        VehicleStatusCard(vehicleStatus)

                        PowerChargingCard(vehicleData)

                        ClimateSummaryCard(vehicleData)

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
                }
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
                            proximityEnabled = proxConfigs[car.vin]?.enabled == true,
                            onSelect = {
                                vin = car.vin
                                selectedName = car.name
                                statusError = null
                                startWithPermissions(car.vin)
                            },
                            onSettings = { settingsVin = car.vin },
                            onDelete = {
                                val updated = carStore.remove(car.vin)
                                cars.clear()
                                cars.addAll(updated)
                                if (vin == car.vin) {
                                    vin = ""
                                    selectedName = null
                                }
                                ProximityRegistry.refresh(ctx)
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
            .padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
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
    proximityEnabled: Boolean,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
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
                    if (proximityEnabled) {
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AccentDim)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "PROX",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                color = Accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
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
                icon = Icons.Filled.Settings,
                tint = Accent,
                bg = GraphiteHi,
                onClick = onSettings,
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

                // Center column floats between the orb and refresh chip; both circles end up
                // vertically centered against this row, putting all three on the same baseline.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stateLabel,
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.8.sp),
                        color = ringColor,
                        fontWeight = FontWeight.SemiBold,
                    )
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
                                "Updated ${formatLocalTime(received)}\n${ageSec}s ago"
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

                Spacer(Modifier.width(14.dp))

                InlineRefreshChip(enabled = refreshEnabled, onClick = onRefresh)
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.BatteryChargingFull,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        val rangeText = "${miles.toInt()} mi"
        val pctText = batteryPct?.let { " · $it%" } ?: ""
        Text(
            rangeText + pctText,
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.4.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InlineRefreshChip(enabled: Boolean, onClick: () -> Unit) {
    // Sized to match LockOrb (82.dp) so the two anchor the hero row symmetrically.
    Box(
        modifier = Modifier
            .size(82.dp)
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
            modifier = Modifier.size(36.dp),
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
                    "Finish on the car",
                    style = MaterialTheme.typography.titleMedium,
                    color = Warning,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "1. Tap your Tesla keycard on the keycard slot behind the cupholders.\n" +
                        "2. Accept the new key prompt on the car's display.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
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
    onComfort: () -> Unit,
    onAdvanced: () -> Unit,
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
                icon = Icons.Filled.ArrowUpward,
                label = "Open trunk",
                enabled = enabled,
                iconSize = 24.dp,
                onClick = onTrunkOpen,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.ArrowDownward,
                label = "Close trunk",
                enabled = enabled,
                iconSize = 24.dp,
                onClick = onTrunkClose,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.ArrowUpward,
                label = "Open port",
                enabled = enabled,
                iconSize = 24.dp,
                onClick = onPortOpen,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.ArrowDownward,
                label = "Close port",
                enabled = enabled,
                iconSize = 24.dp,
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
                icon = Icons.Filled.Thermostat,
                label = "Comfort",
                enabled = infotainmentEnabled,
                onClick = onComfort,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Tune,
                label = "Advanced",
                enabled = infotainmentEnabled,
                onClick = onAdvanced,
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
    iconSize: Dp = 17.dp,
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
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
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
private fun ProximityToggleCard(
    vin: String,
    carStore: CarStore,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val configs by ProximityRegistry.configs.collectAsState()
    // Mirror persisted state so the switch reflects toggles immediately while the registry
    // refresh round-trips through SharedPreferences + service start/stop.
    var enabled by remember(vin) { mutableStateOf(carStore.getProximity(vin).enabled) }
    LaunchedEffect(configs, vin) {
        enabled = configs[vin]?.enabled ?: carStore.getProximity(vin).enabled
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* harmless if denied — notification just won't be visible */ }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Graphite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Proximity unlock",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (enabled) "On — tap to calibrate" else "Off — tap to set up",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    enabled = on
                    val cur = carStore.getProximity(vin)
                    carStore.setProximity(vin, cur.copy(enabled = on))
                    ProximityRegistry.refresh(ctx)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink,
                    checkedTrackColor = Accent,
                    uncheckedTrackColor = GraphiteHi,
                    uncheckedBorderColor = Hairline,
                ),
            )
        }
    }
}

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
                    "Waiting for vehicle status…",
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

@Composable
private fun PowerChargingCard(vehicleData: TeslaSession.VehicleDataSnapshot?) {
    val cs = vehicleData?.data?.chargeState ?: return
    if (!cs.hasChargingState()) return
    val state = cs.chargingState.typeCase
    val isCharging = state == Vehicle.ChargeState.ChargingState.TypeCase.CHARGING ||
        state == Vehicle.ChargeState.ChargingState.TypeCase.STARTING
    val limitMinutes = if (cs.hasMinutesToChargeLimit() && cs.minutesToChargeLimit > 0) {
        cs.minutesToChargeLimit
    } else null
    val fullMinutes = if (cs.hasMinutesToFullCharge() && cs.minutesToFullCharge > 0) {
        cs.minutesToFullCharge
    } else null

    SectionLabel("Power & charging")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Graphite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusRow("State", chargingStateLabel(cs), tone = chargingTone(cs))
            if (cs.hasChargerPower() && cs.chargerPower > 0) {
                val voltage = if (cs.hasChargerVoltage() && cs.chargerVoltage > 0) " · ${cs.chargerVoltage}V" else ""
                val amps = if (cs.hasChargerActualCurrent() && cs.chargerActualCurrent > 0) " · ${cs.chargerActualCurrent}A" else ""
                StatusRow("Power", "${cs.chargerPower} kW$voltage$amps", tone = Tone.Good)
            } else if (cs.hasChargeRateMphFloat() && cs.chargeRateMphFloat > 0f) {
                StatusRow("Rate", "${"%.1f".format(cs.chargeRateMphFloat)} mph", tone = Tone.Good)
            }
            if (isCharging) {
                limitMinutes?.let { StatusRow("To limit", formatMinutes(it), tone = Tone.Neutral) }
                fullMinutes?.let { StatusRow("To full", formatMinutes(it), tone = Tone.Neutral) }
            }
            if (cs.hasChargeLimitSoc()) {
                StatusRow("Limit", "${cs.chargeLimitSoc}%", tone = Tone.Neutral)
            }
            if (cs.hasChargeEnergyAdded() && cs.chargeEnergyAdded > 0f) {
                StatusRow("Energy added", "${"%.1f".format(cs.chargeEnergyAdded)} kWh", tone = Tone.Neutral)
            }
        }
    }
}

@Composable
private fun ClimateSummaryCard(vehicleData: TeslaSession.VehicleDataSnapshot?) {
    val climate = vehicleData?.data?.climateState ?: return
    val hasAny = climate.hasIsClimateOn() ||
        climate.hasInsideTempCelsius() ||
        climate.hasOutsideTempCelsius() ||
        climate.hasDriverTempSetting()
    if (!hasAny) return

    val on = climate.hasIsClimateOn() && climate.isClimateOn
    val inside = if (climate.hasInsideTempCelsius()) "%.0f°C".format(climate.insideTempCelsius) else null
    val outside = if (climate.hasOutsideTempCelsius()) "%.0f°C".format(climate.outsideTempCelsius) else null
    val setpoint = if (climate.hasDriverTempSetting()) "%.0f°C".format(climate.driverTempSetting) else null
    val keeperLabel = climate.climateKeeperLabel()
    val stwHeat = climate.steeringWheelHeatActive()

    SectionLabel("Climate")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Graphite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusRow(
                "Climate",
                if (on) "ON" else "OFF",
                tone = if (on) Tone.Good else Tone.Neutral,
            )
            inside?.let { StatusRow("Cabin", it, tone = Tone.Neutral) }
            outside?.let { StatusRow("Outside", it, tone = Tone.Neutral) }
            setpoint?.let { StatusRow("Set", it, tone = Tone.Neutral) }
            if (climate.hasIsPreconditioning() && climate.isPreconditioning) {
                StatusRow("Preconditioning", "ON", tone = Tone.Good)
            }
            if (stwHeat) {
                StatusRow("Wheel heat", "ON", tone = Tone.Good)
            }
            keeperLabel?.let { StatusRow("Climate keeper", it, tone = Tone.Good) }
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

// region — Subscreen scaffolding

@Composable
private fun SubCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Graphite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun PillBtn(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    selectedBackground: Color = AccentDim,
    selectedForeground: Color = Accent,
    onClick: () -> Unit,
) {
    val bg = if (selected) selectedBackground else GraphiteHi
    val fg = if (selected) selectedForeground else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) fg else TextMuted,
        )
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = TextMuted,
    )
}

// endregion

// region — Comfort subscreen

@Composable
private fun ComfortScreen(
    enabled: Boolean,
    vehicleData: TeslaSession.VehicleDataSnapshot?,
    onBack: () -> Unit,
    run: (suspend () -> Unit) -> Unit,
    session: TeslaSession,
) {
    val climate = vehicleData?.data?.climateState
    val driverTempC = if (climate?.hasDriverTempSetting() == true) climate.driverTempSetting else 21f
    val passengerTempC = if (climate?.hasPassengerTempSetting() == true) climate.passengerTempSetting else driverTempC
    val climateOn = climate?.hasIsClimateOn() == true && climate.isClimateOn
    val stwHeat = climate?.steeringWheelHeatActive() == true
    val bio = climate?.hasBioweaponModeOn() == true && climate.bioweaponModeOn
    val precondMax = climate?.hasIsPreconditioning() == true && climate.isPreconditioning

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackPill(label = "Controls", onClick = onBack)

        SectionLabel("Climate")
        SubCard {
            val inside = climate?.takeIf { it.hasInsideTempCelsius() }?.insideTempCelsius
            val outside = climate?.takeIf { it.hasOutsideTempCelsius() }?.outsideTempCelsius
            if (inside != null || outside != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SubLabel("CABIN")
                        Text(
                            inside?.let { "${"%.1f".format(it)}°C" } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        SubLabel("OUTSIDE")
                        Text(
                            outside?.let { "${"%.1f".format(it)}°C" } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Thermostat,
                    label = if (climateOn) "Climate ON" else "Turn ON",
                    enabled = enabled,
                    accent = if (climateOn) Success else Accent,
                    onClick = { run { session.climateOn() } },
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.PowerSettingsNew,
                    label = "Turn OFF",
                    enabled = enabled,
                    onClick = { run { session.climateOff() } },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SubLabel("SET · DRIVER / PASSENGER")
                    Text(
                        "${"%.1f".format(driverTempC)}°C  ·  ${"%.1f".format(passengerTempC)}°C",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val cool = 20.0f
                    val warm = (72f - 32f) * 5f / 9f
                    PillBtn(
                        modifier = Modifier.weight(1f),
                        text = "68",
                        selected = false,
                        enabled = enabled,
                        onClick = {
                            run { session.setClimateTemperature(cool, cool) }
                        },
                    )
                    PillBtn(
                        modifier = Modifier.weight(1f),
                        text = "72",
                        selected = false,
                        enabled = enabled,
                        onClick = {
                            run { session.setClimateTemperature(warm, warm) }
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OptimisticTogglePill(
                    modifier = Modifier.weight(1f),
                    labelPrefix = "Wheel heat",
                    state = stwHeat,
                    enabled = enabled,
                    onToggle = { run { session.setSteeringWheelHeater(it) } },
                )
                OptimisticTogglePill(
                    modifier = Modifier.weight(1f),
                    labelPrefix = "Precond Max",
                    state = precondMax,
                    enabled = enabled,
                    onToggle = { run { session.setPreconditioningMax(it) } },
                )
            }
            OptimisticTogglePill(
                modifier = Modifier.fillMaxWidth(),
                labelPrefix = "Bioweapon defense",
                state = bio,
                enabled = enabled,
                onToggle = { run { session.setBioweaponMode(it) } },
            )
        }

        SectionLabel("Audio")
        SubCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.VolumeDown,
                    label = "Volume −",
                    enabled = enabled,
                    onClick = { run { session.bumpVolume(-1) } },
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = "Volume +",
                    enabled = enabled,
                    onClick = { run { session.bumpVolume(1) } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.SkipPrevious,
                    label = "Prev track",
                    enabled = enabled,
                    onClick = { run { session.mediaPreviousTrack() } },
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.PlayArrow,
                    label = "Play / Pause",
                    enabled = enabled,
                    onClick = { run { session.mediaTogglePlayback() } },
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.SkipNext,
                    label = "Next track",
                    enabled = enabled,
                    onClick = { run { session.mediaNextTrack() } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.SkipPrevious,
                    label = "Prev favorite",
                    enabled = enabled,
                    onClick = { run { session.mediaPreviousFavorite() } },
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.SkipNext,
                    label = "Next favorite",
                    enabled = enabled,
                    onClick = { run { session.mediaNextFavorite() } },
                )
            }
        }

        SectionLabel("Seats")
        SubCard {
            SeatRow(
                label = "Driver heat",
                current = climate?.seatHeaterLeftIfHas(),
                enabled = enabled,
                onSet = { lvl -> run { session.setSeatHeater(TeslaSession.SeatPosition.FRONT_LEFT, lvl) } },
            )
            SeatRow(
                label = "Passenger heat",
                current = climate?.seatHeaterRightIfHas(),
                enabled = enabled,
                onSet = { lvl -> run { session.setSeatHeater(TeslaSession.SeatPosition.FRONT_RIGHT, lvl) } },
            )
            Divider()
            SeatRow(
                label = "Rear left heat",
                current = climate?.seatHeaterRearLeftIfHas(),
                enabled = enabled,
                onSet = { lvl -> run { session.setSeatHeater(TeslaSession.SeatPosition.REAR_LEFT, lvl) } },
            )
            SeatRow(
                label = "Rear center heat",
                current = climate?.seatHeaterRearCenterIfHas(),
                enabled = enabled,
                onSet = { lvl -> run { session.setSeatHeater(TeslaSession.SeatPosition.REAR_CENTER, lvl) } },
            )
            SeatRow(
                label = "Rear right heat",
                current = climate?.seatHeaterRearRightIfHas(),
                enabled = enabled,
                onSet = { lvl -> run { session.setSeatHeater(TeslaSession.SeatPosition.REAR_RIGHT, lvl) } },
            )
            Divider()
            SeatCoolerRow(
                label = "Driver cool",
                current = climate?.seatFanFrontLeftIfHas(),
                enabled = enabled,
                onSet = { lvl -> run { session.setSeatCooler(TeslaSession.SeatPosition.FRONT_LEFT, lvl) } },
            )
            SeatCoolerRow(
                label = "Passenger cool",
                current = climate?.seatFanFrontRightIfHas(),
                enabled = enabled,
                onSet = { lvl -> run { session.setSeatCooler(TeslaSession.SeatPosition.FRONT_RIGHT, lvl) } },
            )
            Divider()
            val autoLeft = climate?.hasAutoSeatClimateLeft() == true && climate.autoSeatClimateLeft
            val autoRight = climate?.hasAutoSeatClimateRight() == true && climate.autoSeatClimateRight
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PillBtn(
                    modifier = Modifier.weight(1f),
                    text = "Auto driver: " + if (autoLeft) "ON" else "OFF",
                    selected = autoLeft,
                    enabled = enabled,
                    onClick = { run { session.setAutoSeatClimate(TeslaSession.SeatPosition.FRONT_LEFT, !autoLeft) } },
                )
                PillBtn(
                    modifier = Modifier.weight(1f),
                    text = "Auto passenger: " + if (autoRight) "ON" else "OFF",
                    selected = autoRight,
                    enabled = enabled,
                    onClick = { run { session.setAutoSeatClimate(TeslaSession.SeatPosition.FRONT_RIGHT, !autoRight) } },
                )
            }
        }

        SectionLabel("Climate keeper")
        SubCard {
            val current = climate?.climateKeeperMode?.typeCase
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ClimateKeeperBtn("Off", current, Vehicle.ClimateState.ClimateKeeperMode.TypeCase.OFF,
                    CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E.ClimateKeeperAction_Off,
                    enabled, run, session, Modifier.weight(1f))
                ClimateKeeperBtn("On", current, Vehicle.ClimateState.ClimateKeeperMode.TypeCase.ON,
                    CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E.ClimateKeeperAction_On,
                    enabled, run, session, Modifier.weight(1f))
                ClimateKeeperBtn("Dog", current, Vehicle.ClimateState.ClimateKeeperMode.TypeCase.DOG,
                    CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E.ClimateKeeperAction_Dog,
                    enabled, run, session, Modifier.weight(1f))
                ClimateKeeperBtn("Camp", current, Vehicle.ClimateState.ClimateKeeperMode.TypeCase.PARTY,
                    CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E.ClimateKeeperAction_Camp,
                    enabled, run, session, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OptimisticTogglePill(
    labelPrefix: String,
    state: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var optimistic by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state) {
        if (optimistic != null && state == optimistic) optimistic = null
    }
    val displayed = optimistic ?: state
    PillBtn(
        modifier = modifier,
        text = "$labelPrefix: " + if (displayed) "ON" else "OFF",
        selected = displayed,
        enabled = enabled,
        onClick = {
            val next = !displayed
            optimistic = next
            onToggle(next)
        },
    )
}

@Composable
private fun SeatRow(
    label: String,
    current: Int?,
    enabled: Boolean,
    onSet: (TeslaSession.HeaterLevel) -> Unit,
) {
    var optimistic by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(current) {
        if (optimistic != null && current != null && current == optimistic) optimistic = null
    }
    val displayed = optimistic ?: current ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SubLabel(label.uppercase())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            heaterLevels.forEachIndexed { idx, (text, lvl) ->
                val active = idx > 0
                PillBtn(
                    modifier = Modifier.weight(1f),
                    text = text,
                    selected = displayed == idx,
                    enabled = enabled,
                    selectedBackground = if (active) DangerDim else AccentDim,
                    selectedForeground = if (active) Danger else Accent,
                    onClick = {
                        optimistic = idx
                        onSet(lvl)
                    },
                )
            }
        }
    }
}

@Composable
private fun SeatCoolerRow(
    label: String,
    current: Int?,
    enabled: Boolean,
    onSet: (TeslaSession.CoolerLevel) -> Unit,
) {
    var optimistic by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(current) {
        if (optimistic != null && current != null && current == optimistic) optimistic = null
    }
    val displayed = optimistic ?: current ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SubLabel(label.uppercase())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            coolerLevels.forEachIndexed { idx, (text, lvl) ->
                val active = idx > 0
                PillBtn(
                    modifier = Modifier.weight(1f),
                    text = text,
                    selected = displayed == idx,
                    enabled = enabled,
                    selectedBackground = if (active) InfoDim else AccentDim,
                    selectedForeground = if (active) Info else Accent,
                    onClick = {
                        optimistic = idx
                        onSet(lvl)
                    },
                )
            }
        }
    }
}

// Proto integer values for the Climate state seat levels:
//   SeatHeaterLevel_E: Off=0, Low=1, Med=2, High=3
//   SeatCoolingLevel_E: Off=0, Low=1, Med=2, High=3
private val heaterLevels = listOf(
    "Off" to TeslaSession.HeaterLevel.OFF,
    "Low" to TeslaSession.HeaterLevel.LOW,
    "Med" to TeslaSession.HeaterLevel.MED,
    "High" to TeslaSession.HeaterLevel.HIGH,
)
private val coolerLevels = listOf(
    "Off" to TeslaSession.CoolerLevel.OFF,
    "Low" to TeslaSession.CoolerLevel.LOW,
    "Med" to TeslaSession.CoolerLevel.MED,
    "High" to TeslaSession.CoolerLevel.HIGH,
)

private fun Vehicle.ClimateState.seatHeaterLeftIfHas(): Int? =
    if (hasSeatHeaterLeft()) seatHeaterLeft else null

private fun Vehicle.ClimateState.seatHeaterRightIfHas(): Int? =
    if (hasSeatHeaterRight()) seatHeaterRight else null

private fun Vehicle.ClimateState.seatHeaterRearLeftIfHas(): Int? =
    if (hasSeatHeaterRearLeft()) seatHeaterRearLeft else null

private fun Vehicle.ClimateState.seatHeaterRearCenterIfHas(): Int? =
    if (hasSeatHeaterRearCenter()) seatHeaterRearCenter else null

private fun Vehicle.ClimateState.seatHeaterRearRightIfHas(): Int? =
    if (hasSeatHeaterRearRight()) seatHeaterRearRight else null

private fun Vehicle.ClimateState.seatFanFrontLeftIfHas(): Int? =
    if (hasSeatFanFrontLeft()) seatFanFrontLeft else null

private fun Vehicle.ClimateState.seatFanFrontRightIfHas(): Int? =
    if (hasSeatFanFrontRight()) seatFanFrontRight else null

private fun Vehicle.ClimateState.steeringWheelHeatActive(): Boolean {
    if (hasSteeringWheelHeater() && steeringWheelHeater) return true
    if (hasSteeringWheelHeatLevel()) {
        val lvl = steeringWheelHeatLevel
        if (lvl == com.tesla.generated.carserver.common.Common.StwHeatLevel.StwHeatLevel_Low ||
            lvl == com.tesla.generated.carserver.common.Common.StwHeatLevel.StwHeatLevel_High
        ) return true
    }
    return false
}

private fun Vehicle.ClimateState.climateKeeperLabel(): String? =
    when (climateKeeperMode?.typeCase) {
        Vehicle.ClimateState.ClimateKeeperMode.TypeCase.ON -> "On"
        Vehicle.ClimateState.ClimateKeeperMode.TypeCase.DOG -> "Dog"
        Vehicle.ClimateState.ClimateKeeperMode.TypeCase.PARTY -> "Camp"
        else -> null
    }

// endregion

// region — Advanced subscreen

@Composable
private fun AdvancedScreen(
    enabled: Boolean,
    vehicleData: TeslaSession.VehicleDataSnapshot?,
    onBack: () -> Unit,
    run: (suspend () -> Unit) -> Unit,
    session: TeslaSession,
) {
    val cs = vehicleData?.data?.chargeState
    val climate = vehicleData?.data?.climateState
    val tires = vehicleData?.data?.tirePressureState
    val media = vehicleData?.data?.mediaState

    val currentLimit = cs?.takeIf { it.hasChargeLimitSoc() }?.chargeLimitSoc
    val currentAmps = cs?.takeIf { it.hasChargingAmps() }?.chargingAmps
    val ampsMax = cs?.takeIf { it.hasChargeCurrentRequestMax() }?.chargeCurrentRequestMax ?: 48
    val charging = cs?.chargingState?.typeCase ==
        Vehicle.ChargeState.ChargingState.TypeCase.CHARGING

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackPill(label = "Controls", onClick = onBack)

        SectionLabel("Charging")
        SubCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Bolt,
                    label = "Start charge",
                    enabled = enabled,
                    accent = if (charging) Success else Accent,
                    onClick = { run { session.chargeStart() } },
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Close,
                    label = "Stop charge",
                    enabled = enabled,
                    onClick = { run { session.chargeStop() } },
                )
            }
            SubLabel("CHARGE LIMIT" + (currentLimit?.let { " · $it%" } ?: ""))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (pct in listOf(50, 60, 70, 80, 90, 100)) {
                    PillBtn(
                        modifier = Modifier.weight(1f),
                        text = "$pct",
                        selected = currentLimit == pct,
                        enabled = enabled,
                        onClick = { run { session.setChargeLimit(pct) } },
                    )
                }
            }
            SubLabel("CHARGING AMPS" + (currentAmps?.let { " · ${it}A" } ?: ""))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (a in ampsPresetsFor(ampsMax)) {
                    PillBtn(
                        modifier = Modifier.weight(1f),
                        text = "${a}A",
                        selected = currentAmps == a,
                        enabled = enabled,
                        onClick = { run { session.setChargingAmps(a) } },
                    )
                }
            }
        }

        SectionLabel("Cabin overheat protection")
        SubCard {
            val cop = climate?.takeIf { it.hasCabinOverheatProtection() }?.cabinOverheatProtection
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                PillBtn(
                    modifier = Modifier.weight(1f),
                    text = "Off",
                    selected = cop == Vehicle.ClimateState.CabinOverheatProtection_E.CabinOverheatProtectionOff,
                    enabled = enabled,
                    onClick = { run { session.setCabinOverheatProtection(on = false) } },
                )
                PillBtn(
                    modifier = Modifier.weight(1f),
                    text = "On",
                    selected = cop == Vehicle.ClimateState.CabinOverheatProtection_E.CabinOverheatProtectionOn,
                    enabled = enabled,
                    onClick = { run { session.setCabinOverheatProtection(on = true) } },
                )
                PillBtn(
                    modifier = Modifier.weight(1f),
                    text = "Fan only",
                    selected = cop == Vehicle.ClimateState.CabinOverheatProtection_E.CabinOverheatProtectionFanOnly,
                    enabled = enabled,
                    onClick = { run { session.setCabinOverheatProtection(on = true, fanOnly = true) } },
                )
            }
        }

        SectionLabel("Vehicle")
        SubCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.FlashOn,
                    label = "Flash lights",
                    enabled = enabled,
                    onClick = { run { session.flashLights() } },
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.NotificationsActive,
                    label = "Honk horn",
                    enabled = enabled,
                    onClick = { run { session.honkHorn() } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Security,
                    label = "Sentry ON",
                    enabled = enabled,
                    accent = Success,
                    onClick = { run { session.setSentryMode(true) } },
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Security,
                    label = "Sentry OFF",
                    enabled = enabled,
                    onClick = { run { session.setSentryMode(false) } },
                )
            }
        }

        SectionLabel("Status")
        AdvancedStatusCard(cs, climate, tires, media)

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ClimateKeeperBtn(
    label: String,
    currentCase: Vehicle.ClimateState.ClimateKeeperMode.TypeCase?,
    matchCase: Vehicle.ClimateState.ClimateKeeperMode.TypeCase,
    sendValue: CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E,
    enabled: Boolean,
    run: (suspend () -> Unit) -> Unit,
    session: TeslaSession,
    modifier: Modifier = Modifier,
) {
    PillBtn(
        modifier = modifier,
        text = label,
        selected = currentCase == matchCase,
        enabled = enabled,
        onClick = { run { session.setClimateKeeperMode(sendValue) } },
    )
}

private fun ampsPresetsFor(max: Int): List<Int> {
    val ceil = max.coerceIn(8, 48)
    return listOf(8, 16, 24, 32, 40, 48).filter { it <= ceil }.ifEmpty { listOf(ceil) }
}

@Composable
private fun AdvancedStatusCard(
    cs: Vehicle.ChargeState?,
    climate: Vehicle.ClimateState?,
    tires: Vehicle.TirePressureState?,
    media: Vehicle.MediaState?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Graphite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (cs == null && climate == null && tires == null && media == null) {
                Text("Waiting for vehicle data…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                return@Column
            }
            if (cs != null) {
                StatusRow("Charging", chargingStateLabel(cs), tone = chargingTone(cs))
                if (cs.hasBatteryLevel()) StatusRow("Battery", "${cs.batteryLevel}%", tone = Tone.Neutral)
                if (cs.hasUsableBatteryLevel()) StatusRow("Usable", "${cs.usableBatteryLevel}%", tone = Tone.Neutral)
                if (cs.hasEstBatteryRange()) StatusRow("Range", "${cs.estBatteryRange.toInt()} mi", tone = Tone.Neutral)
                if (cs.hasChargeLimitSoc()) StatusRow("Limit", "${cs.chargeLimitSoc}%", tone = Tone.Neutral)
                if (cs.hasChargerPower() && cs.chargerPower > 0)
                    StatusRow("Power", "${cs.chargerPower} kW", tone = Tone.Neutral)
                if (cs.hasChargerActualCurrent() && cs.chargerActualCurrent > 0)
                    StatusRow("Current", "${cs.chargerActualCurrent} A", tone = Tone.Neutral)
                if (cs.hasMinutesToFullCharge() && cs.minutesToFullCharge > 0)
                    StatusRow("Time to full", formatMinutes(cs.minutesToFullCharge), tone = Tone.Neutral)
                Divider()
            }
            if (climate != null) {
                if (climate.hasInsideTempCelsius())
                    StatusRow("Inside", "${"%.1f".format(climate.insideTempCelsius)}°C", tone = Tone.Neutral)
                if (climate.hasOutsideTempCelsius())
                    StatusRow("Outside", "${"%.1f".format(climate.outsideTempCelsius)}°C", tone = Tone.Neutral)
                if (climate.hasDriverTempSetting())
                    StatusRow("Driver set", "${"%.1f".format(climate.driverTempSetting)}°C", tone = Tone.Neutral)
                if (climate.hasPassengerTempSetting())
                    StatusRow("Passenger set", "${"%.1f".format(climate.passengerTempSetting)}°C", tone = Tone.Neutral)
                if (climate.hasFanStatus())
                    StatusRow("Fan", "${climate.fanStatus}/10", tone = Tone.Neutral)
                Divider()
            }
            if (tires != null && hasAnyTire(tires)) {
                if (tires.hasTpmsPressureFl())
                    StatusRow("Tire FL", "${"%.2f".format(tires.tpmsPressureFl)} bar", tone = Tone.Neutral)
                if (tires.hasTpmsPressureFr())
                    StatusRow("Tire FR", "${"%.2f".format(tires.tpmsPressureFr)} bar", tone = Tone.Neutral)
                if (tires.hasTpmsPressureRl())
                    StatusRow("Tire RL", "${"%.2f".format(tires.tpmsPressureRl)} bar", tone = Tone.Neutral)
                if (tires.hasTpmsPressureRr())
                    StatusRow("Tire RR", "${"%.2f".format(tires.tpmsPressureRr)} bar", tone = Tone.Neutral)
                Divider()
            }
            if (media != null) {
                if (media.hasAudioVolume() && media.hasAudioVolumeMax())
                    StatusRow("Volume", "${"%.1f".format(media.audioVolume)} / ${"%.1f".format(media.audioVolumeMax)}", tone = Tone.Neutral)
                if (media.hasNowPlayingTitle() && media.nowPlayingTitle.isNotBlank())
                    StatusRow("Title", media.nowPlayingTitle, tone = Tone.Neutral)
                if (media.hasNowPlayingArtist() && media.nowPlayingArtist.isNotBlank())
                    StatusRow("Artist", media.nowPlayingArtist, tone = Tone.Neutral)
            }
        }
    }
}

private fun hasAnyTire(t: Vehicle.TirePressureState): Boolean =
    t.hasTpmsPressureFl() || t.hasTpmsPressureFr() || t.hasTpmsPressureRl() || t.hasTpmsPressureRr()

private fun chargingStateLabel(cs: Vehicle.ChargeState): String = when (cs.chargingState.typeCase) {
    Vehicle.ChargeState.ChargingState.TypeCase.DISCONNECTED -> "Disconnected"
    Vehicle.ChargeState.ChargingState.TypeCase.NOPOWER -> "No power"
    Vehicle.ChargeState.ChargingState.TypeCase.STARTING -> "Starting"
    Vehicle.ChargeState.ChargingState.TypeCase.CHARGING -> "Charging"
    Vehicle.ChargeState.ChargingState.TypeCase.COMPLETE -> "Complete"
    Vehicle.ChargeState.ChargingState.TypeCase.STOPPED -> "Stopped"
    Vehicle.ChargeState.ChargingState.TypeCase.CALIBRATING -> "Calibrating"
    else -> "Unknown"
}

private fun chargingTone(cs: Vehicle.ChargeState): Tone = when (cs.chargingState.typeCase) {
    Vehicle.ChargeState.ChargingState.TypeCase.CHARGING -> Tone.Good
    Vehicle.ChargeState.ChargingState.TypeCase.COMPLETE -> Tone.Good
    Vehicle.ChargeState.ChargingState.TypeCase.STOPPED -> Tone.Warn
    Vehicle.ChargeState.ChargingState.TypeCase.NOPOWER -> Tone.Warn
    else -> Tone.Neutral
}

private fun formatMinutes(min: Int): String {
    if (min <= 0) return "—"
    val h = min / 60
    val m = min % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

// endregion

// region — Proximity settings

@Composable
private fun ProximitySettingsScreen(
    car: SavedCar,
    controller: CarController,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember(car.vin) { CarStore(ctx) }
    var cfg by remember(car.vin) { mutableStateOf(store.getProximity(car.vin)) }
    val live by ProximityRegistry.live.collectAsState()
    val serviceState by ProximityRegistry.serviceState.collectAsState()
    val liveState = live[car.vin]
    val coScope = rememberCoroutineScope()
    var pairError by remember { mutableStateOf<String?>(null) }

    val session = controller.session.collectAsState().value
    val sessionStatus = session?.status?.collectAsState()?.value
    val enrollment = session?.enrollment?.collectAsState()?.value
    val phase = controller.phase.collectAsState().value
    val connection = controller.connection.collectAsState().value
    val connState = connection?.state?.collectAsState()?.value
    val connReady = connState is CarConnection.State.Ready

    // Auto-start the controller for this car while the settings screen is open
    // so Session/Enroll have something to talk to. onBack stops it.
    LaunchedEffect(car.vin) { onStart() }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* harmless if denied — notification just won't be visible */ }

    fun persist(newCfg: ProximityConfig) {
        cfg = newCfg
        store.setProximity(car.vin, newCfg)
        ProximityRegistry.refresh(ctx)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackPill(label = "Vehicles", onClick = onBack)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Graphite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${car.name} · VIN ••••••${car.vin.takeLast(6)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        SectionLabel("Pair this phone")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Graphite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Show the keycard prompt as soon as the user presses Enroll. VCSEC's
                // OPERATIONSTATUS_WAIT (which moves us to AwaitingKeycard) can lag by a
                // second or two, and a blank UI in the interim hides what the user is
                // supposed to do (tap a registered card on the center-console reader).
                if (enrollment is TeslaSession.Enrollment.Requested ||
                    enrollment is TeslaSession.Enrollment.AwaitingKeycard
                ) {
                    KeycardPrompt()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        modifier = Modifier.weight(1f),
                        text = "Session",
                        icon = Icons.Filled.Sync,
                        enabled = connReady && session != null,
                        onClick = {
                            val sess = session ?: return@SecondaryButton
                            coScope.launch {
                                runCatching {
                                    sess.requestSessionInfo(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY)
                                }.onFailure { pairError = it.message }
                            }
                        },
                    )
                    SecondaryButton(
                        modifier = Modifier.weight(1f),
                        text = "Enroll",
                        icon = Icons.Filled.Key,
                        enabled = connReady && session != null,
                        onClick = {
                            val sess = session ?: return@SecondaryButton
                            coScope.launch {
                                runCatching { sess.requestEnrollment() }
                                    .onFailure { pairError = it.message }
                            }
                        },
                    )
                }
                val sub = when {
                    enrollment is TeslaSession.Enrollment.Success -> "Paired"
                    enrollment is TeslaSession.Enrollment.AwaitingKeycard -> "Awaiting keycard tap…"
                    enrollment is TeslaSession.Enrollment.Requested -> "Sent enroll request — tap keycard on center console"
                    enrollment is TeslaSession.Enrollment.Failed -> "Enrollment failed: ${enrollment.reason}"
                    sessionStatus is TeslaSession.Status.Failed -> "Session failed: ${sessionStatus.reason}"
                    sessionStatus is TeslaSession.Status.Requested -> "Requesting session info…"
                    sessionStatus is TeslaSession.Status.Established &&
                        sessionStatus.statusEnum ==
                        com.tesla.generated.signatures.Signatures.Session_Info_Status.SESSION_INFO_STATUS_OK ->
                        "Session OK"
                    connReady -> "Connected. Tap Session, then Enroll."
                    else -> phaseLabel(phase)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                pairError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Danger)
                }
            }
        }

        SectionLabel("Phone-as-keycard")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Graphite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Register this phone as an NFC keycard. Once paired, tap the phone on " +
                        "the center console to authorize a drive — same gesture as a physical " +
                        "Tesla keycard. Note: you will need an existing keycard tap to authorize " +
                        "adding the phone over Bluetooth and NFC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                SecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Pair phone as keycard",
                    icon = Icons.Filled.Nfc,
                    enabled = connReady && session != null,
                    onClick = {
                        val sess = session ?: return@SecondaryButton
                        coScope.launch {
                            runCatching {
                                val keycardPub = KeycardIdentity.load().publicKeyBytes()
                                sess.requestKeycardEnrollment(keycardPub)
                            }.onFailure { pairError = it.message }
                        }
                    },
                )
                Text(
                    "After pairing: wake your phone and hold it flat against the keycard slot " +
                        "behind the cupholders to drive.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }

        SectionLabel("Proximity unlock")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Graphite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Enable",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Auto-unlock when this phone approaches the car, and auto-lock when it walks away.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = cfg.enabled,
                        onCheckedChange = { on ->
                            if (on &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            persist(cfg.copy(enabled = on))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Accent,
                            uncheckedTrackColor = GraphiteHi,
                            uncheckedBorderColor = Hairline,
                        ),
                    )
                }
            }
        }

        // Lock-delay warning, prominent so users don't expect snappy auto-lock.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Graphite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Warning.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Whatshot,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Auto-lock isn't instant",
                        style = MaterialTheme.typography.titleSmall,
                        color = Warning,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Lock fires once the phone's signal stays weak for the dwell period, " +
                            "not the instant you step away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        SectionLabel("Calibration", trailing = serviceStateLabel(serviceState))
        SubCard {
            // Live readout — helps user pick thresholds.
            val emaInt = liveState?.ema?.roundToInt()
            val lastRssi = liveState?.lastRssi
            val fsmStateText = when (liveState?.fsmState) {
                ProximityFsm.State.Near -> "NEAR"
                ProximityFsm.State.Far, null -> "FAR"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Live RSSI",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = TextMuted,
                    )
                    Text(
                        when {
                            emaInt != null -> "${emaInt} dBm (smoothed)"
                            cfg.enabled -> "Waiting for beacon…"
                            else -> "Enable to calibrate"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (lastRssi != null) {
                        Text(
                            "Last sample $lastRssi dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(GraphiteHi)
                        .border(1.dp, Hairline, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        fsmStateText,
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                        color = if (fsmStateText == "NEAR") Success else TextMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            val unlockMin = ProximityConfig.MIN_RSSI + ProximityConfig.MIN_HYSTERESIS_DB
            SubLabel("UNLOCK WHEN SIGNAL ≥ ${cfg.unlockRssi} dBm (stronger = closer)")
            Slider(
                value = cfg.unlockRssi.toFloat().coerceIn(unlockMin.toFloat(), ProximityConfig.MAX_RSSI.toFloat()),
                onValueChange = { v ->
                    val newUnlock = v.roundToInt()
                        .coerceIn(unlockMin, ProximityConfig.MAX_RSSI)
                    val newLock = min(cfg.lockRssi, newUnlock - ProximityConfig.MIN_HYSTERESIS_DB)
                        .coerceAtLeast(ProximityConfig.MIN_RSSI)
                    persist(cfg.copy(unlockRssi = newUnlock, lockRssi = newLock))
                },
                valueRange = unlockMin.toFloat()..ProximityConfig.MAX_RSSI.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = GraphiteHi,
                ),
            )

            val lockMax = cfg.unlockRssi - ProximityConfig.MIN_HYSTERESIS_DB
            SubLabel("LOCK WHEN SIGNAL ≤ ${cfg.lockRssi} dBm (weaker = farther)")
            Slider(
                value = cfg.lockRssi.toFloat().coerceIn(ProximityConfig.MIN_RSSI.toFloat(), lockMax.toFloat()),
                onValueChange = { v ->
                    val newLock = v.roundToInt()
                        .coerceIn(ProximityConfig.MIN_RSSI, lockMax)
                    persist(cfg.copy(lockRssi = newLock))
                },
                valueRange = ProximityConfig.MIN_RSSI.toFloat()..lockMax.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = GraphiteHi,
                ),
            )

            SubLabel("APPROACH DWELL · ${(cfg.enterDwellMs / 1000.0).formatSec()} s")
            Slider(
                value = cfg.enterDwellMs.toFloat() / 1000f,
                onValueChange = { v ->
                    val newDwell = (max(0.5f, v) * 1000f).toLong()
                    persist(cfg.copy(enterDwellMs = newDwell))
                },
                valueRange = 0.5f..10f,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = GraphiteHi,
                ),
            )

            SubLabel("DEPART DWELL · ${(cfg.exitDwellMs / 1000.0).formatSec()} s")
            Slider(
                value = cfg.exitDwellMs.toFloat() / 1000f,
                onValueChange = { v ->
                    val newDwell = (max(5f, v) * 1000f).toLong()
                    persist(cfg.copy(exitDwellMs = newDwell))
                },
                valueRange = 5f..120f,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = GraphiteHi,
                ),
            )
        }

        liveState?.lastAction?.let { action ->
            val ts = liveState.lastActionMs
            val label = when (action) {
                ProximityFsm.Action.Unlock -> "Last action: UNLOCK"
                ProximityFsm.Action.Lock -> "Last action: LOCK"
            }
            val nowMs = rememberNowMs()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Graphite,
                border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (action == ProximityFsm.Action.Unlock) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    if (ts != null) {
                        val ageSec = ((nowMs - ts) / 1000).coerceAtLeast(0)
                        Text(
                            "${ageSec}s ago",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        // Single-car users can hide the "Vehicles" back pill to declutter the active screen.
        // With 2+ cars the back pill is the only way to switch, so this toggle is hidden too.
        if (store.list().size <= 1) {
            SectionLabel("App preferences")
            var hidePill by remember { mutableStateOf(store.hideBackToVehicles()) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Graphite,
                border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Hide \"Vehicles\" button",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Removes the back-to-vehicles pill on the car screen. Useful when you only have one car.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = hidePill,
                        onCheckedChange = { on ->
                            hidePill = on
                            store.setHideBackToVehicles(on)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Accent,
                            uncheckedTrackColor = GraphiteHi,
                            uncheckedBorderColor = Hairline,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun Double.formatSec(): String = if (this >= 10) "${this.toInt()}" else "%.1f".format(this)

private fun serviceStateLabel(s: ProximityRegistry.ServiceState): String = when (s) {
    ProximityRegistry.ServiceState.Stopped -> "off"
    ProximityRegistry.ServiceState.Scanning -> "scanning"
    ProximityRegistry.ServiceState.Commanding -> "commanding"
    ProximityRegistry.ServiceState.Idle -> "idle"
    ProximityRegistry.ServiceState.WaitingForBluetooth -> "waiting for bluetooth"
}

// endregion
