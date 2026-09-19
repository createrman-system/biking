package com.createrman.biking

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.createrman.biking.domain.model.AppThemeMode
import com.createrman.biking.domain.model.RideSummary
import com.createrman.biking.domain.model.SpeedUnit
import com.createrman.biking.domain.model.TrackingStatus
import com.createrman.biking.domain.model.TrackingUiState
import com.createrman.biking.ui.BikingViewModel
import com.createrman.biking.ui.theme.BikingTheme
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: BikingViewModel = koinViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
            }
            BikingTheme(darkTheme = darkTheme, dynamicColor = false) {
                BikingAppScreen(viewModel)
            }
        }
    }
}

private enum class AppTab { Ride, Map, History, Settings, About }

@Composable
private fun BikingAppScreen(viewModel: BikingViewModel) {
    val context = LocalContext.current
    val state by viewModel.trackingState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rides by viewModel.rides.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(AppTab.Ride) }
    var selectedMapRoute by remember { mutableStateOf<List<Pair<Double, Double>>?>(null) }
    var selectedMapLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var rideName by remember { mutableStateOf("") }
    var rideNote by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val hasPermission = rememberPermissionState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    LaunchedEffect(rideName, rideNote) {
        viewModel.updateMetadata(rideName, rideNote)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems().forEach { (item, label, icon) ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!hasPermission) {
                PermissionScreen {
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    launcher.launch(permissions.toTypedArray())
                }
            } else {
                when (tab) {
                    AppTab.Ride -> RideScreen(
                        state = state,
                        unit = settings.speedUnit,
                        rideName = rideName,
                        rideNote = rideNote,
                        onName = { rideName = it },
                        onNote = { rideNote = it },
                        onStart = {
                            selectedMapRoute = null
                            selectedMapLocation = null
                            val intent = Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                            viewModel.updateMetadata(rideName, rideNote)
                        },
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onStop = { viewModel.stop(rideName, rideNote) },
                    )
                    AppTab.Map -> MapScreen(
                        state = state,
                        unit = settings.speedUnit,
                        routePoints = selectedMapRoute ?: state.route.map { it.latitude to it.longitude },
                        currentLocation = selectedMapLocation ?: state.currentLocation?.let { it.latitude to it.longitude },
                        bearingDegrees = state.bearingDegrees,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppTab.History -> HistoryScreen(
                        rides = rides,
                        unit = settings.speedUnit,
                        onOpen = { ride ->
                            scope.launch {
                                val details = viewModel.getRideDetails(ride.id) ?: return@launch
                                selectedMapRoute = details.points.map { it.latitude to it.longitude }
                                selectedMapLocation = details.points.lastOrNull()?.let { it.latitude to it.longitude }
                                tab = AppTab.Map
                            }
                        },
                    )
                    AppTab.Settings -> SettingsScreen(
                        unit = settings.speedUnit,
                        theme = settings.themeMode,
                        autoPauseEnabled = settings.autoPauseEnabled,
                        onUnit = viewModel::setSpeedUnit,
                        onTheme = viewModel::setThemeMode,
                        onAutoPause = viewModel::setAutoPause,
                    )
                    AppTab.About -> AboutScreen()
                }
            }
        }
    }
}

@Composable
private fun rememberPermissionState(): Boolean {
    val context = LocalContext.current
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.MyLocation, null, Modifier.size(56.dp))
        Text("GPS permission required", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Biking needs location access to record speed, distance, elevation and route history.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onRequest) { Text("Grant permissions") }
    }
}

@Composable
private fun RideScreen(
    state: TrackingUiState,
    unit: SpeedUnit,
    rideName: String,
    rideNote: String,
    onName: (String) -> Unit,
    onNote: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val speed = state.speedMetersPerSecond * unit.multiplier
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (landscape) Arrangement.Center else Arrangement.Top,
    ) {
        Speedometer(speed, unit, landscape)
        Spacer(Modifier.height(16.dp))
        StatsGrid(state, unit)
        Spacer(Modifier.height(16.dp))
        if (state.status == TrackingStatus.Idle) {
            OutlinedTextField(rideName, onName, Modifier.fillMaxWidth(), label = { Text("Ride name") }, singleLine = true)
            OutlinedTextField(rideNote, onNote, Modifier.fillMaxWidth(), label = { Text("Note") }, maxLines = 2)
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.status) {
                TrackingStatus.Idle -> Button(onStart, Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("Start")
                }
                TrackingStatus.Tracking -> {
                    ElevatedButton(onPause, Modifier.weight(1f)) {
                        Icon(Icons.Default.Pause, null)
                        Text("Pause")
                    }
                    Button(onStop, Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, null)
                        Text("Stop")
                    }
                }
                TrackingStatus.Paused, TrackingStatus.AutoPaused -> {
                    Button(onResume, Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text("Resume")
                    }
                    Button(onStop, Modifier.weight(1f)) {
                        Icon(Icons.Default.Save, null)
                        Text("Save")
                    }
                }
            }
        }
        Text(
            text = "${state.status.name} • ${state.sensorMode}${if (!state.isGpsEnabled) " • GPS off" else ""}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun Speedometer(speed: Float, unit: SpeedUnit, landscape: Boolean) {
    Box(
        Modifier.size(if (landscape) 300.dp else 260.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format(Locale.US, "%.1f", speed), fontSize = if (landscape) 88.sp else 72.sp, fontWeight = FontWeight.ExtraBold)
            Text(unit.label.uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatsGrid(state: TrackingUiState, unit: SpeedUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Distance", "%.2f km".format(Locale.US, state.distanceMeters / 1000.0), Modifier.weight(1f))
            StatCard("Time", formatDuration(state.movingTimeSeconds), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Avg", "%.1f %s".format(Locale.US, state.averageSpeedMetersPerSecond * unit.multiplier, unit.label), Modifier.weight(1f))
            StatCard("Elevation", "+%.0f / -%.0f m".format(Locale.US, state.elevationGainMeters, state.elevationLossMeters), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun HistoryScreen(rides: List<RideSummary>, unit: SpeedUnit, onOpen: (RideSummary) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(rides) { ride ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(ride) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(ride.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(DateFormat.getDateTimeInstance().format(Date(ride.startedAtMillis)))
                    Text("%.2f km • %s • %.1f %s • +%.0f m".format(Locale.US, ride.distanceMeters / 1000.0, formatDuration(ride.movingTimeSeconds), ride.averageSpeedMetersPerSecond * unit.multiplier, unit.label, ride.elevationGainMeters))
                    if (ride.note.isNotBlank()) Text(ride.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap to open on map", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    unit: SpeedUnit,
    theme: AppThemeMode,
    autoPauseEnabled: Boolean,
    onUnit: (SpeedUnit) -> Unit,
    onTheme: (AppThemeMode) -> Unit,
    onAutoPause: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedUnit.entries.forEach { item ->
                FilterChip(selected = unit == item, onClick = { onUnit(item) }, label = { Text(item.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { item ->
                FilterChip(selected = theme == item, onClick = { onTheme(item) }, label = { Text(item.name) })
            }
        }
        FilterChip(selected = autoPauseEnabled, onClick = { onAutoPause(!autoPauseEnabled) }, label = { Text("Auto pause") })
    }
}

@Composable
private fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    val githubUrl = "https://github.com/createrman-system/biking"
    
    val annotatedString = buildAnnotatedString {
        append("GitHub: ")
        pushStringAnnotation(tag = "URL", annotation = githubUrl)
        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
            append(githubUrl)
        }
        pop()
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Biking", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Version 2.0.0")
        Text("High-accuracy bike tracking with GPS, motion sensors, local ride history and GPX export.")
        Text(
            text = annotatedString,
            modifier = Modifier.clickable {
                annotatedString.getStringAnnotations(tag = "URL", start = 0, end = annotatedString.length)
                    .firstOrNull()?.let { annotation ->
                        uriHandler.openUri(annotation.item)
                    }
            }
        )
        Text("Privacy: ride data stays on this device.")
    }
}

private fun navItems() = listOf(
    Triple(AppTab.Ride, "Ride", Icons.Default.Speed),
    Triple(AppTab.Map, "Map", Icons.Default.Map),
    Triple(AppTab.History, "History", Icons.Default.History),
    Triple(AppTab.Settings, "Settings", Icons.Default.Settings),
    Triple(AppTab.About, "About", Icons.Default.Info),
)

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, secs)
}
