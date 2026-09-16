package com.createrman.biking

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.createrman.biking.ui.theme.BikingTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var locationManager: LocationManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        locationManager = LocationManager.getInstance(this)
        
        setContent {
            BikingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        locationManager = locationManager,
                        modifier = Modifier.padding(innerPadding),
                        context = this@MainActivity,
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Removed locationManager.stopTracking() to allow background tracking
    }
}

@Composable
fun MainScreen(
    locationManager: LocationManager,
    modifier: Modifier = Modifier,
    context: ComponentActivity
) {
    var viewMode by remember { mutableStateOf(ViewMode.SPEEDOMETER) }
    val isTracking by locationManager.isTracking.collectAsState()
    
    var hasLocationPermission by remember {
        mutableStateOf(
            value = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true)
        )
    }
    
    var isStartingTracking by remember { mutableStateOf(value = false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    }
    
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && !isTracking && !isStartingTracking) {
            isStartingTracking = true
            val intent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isStartingTracking = false
        }
    }
    
    if (!hasLocationPermission) {
        PermissionScreen(
            permissionLauncher = permissionLauncher,
            modifier = modifier
        )
    } else {
        when (viewMode) {
            ViewMode.SPEEDOMETER -> SpeedTrackerScreen(
                locationManager = locationManager,
                modifier = modifier,
                context = context
            ) { viewMode = ViewMode.MAP }
            ViewMode.MAP -> MapScreenWithControls(
                locationManager = locationManager,
                isTracking = isTracking,
                onTrackingToggle = {
                    val intent = Intent(context, TrackingService::class.java)
                    if (isTracking) {
                        intent.action = TrackingService.ACTION_STOP
                    } else {
                        intent.action = TrackingService.ACTION_START
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                },
                onSwitchToSpeedometer = { viewMode = ViewMode.SPEEDOMETER },
                modifier = modifier
            )
        }
    }
}

enum class ViewMode {
    SPEEDOMETER,
    MAP
}

@Composable
fun PermissionScreen(
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Location & Sensor Permissions Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "This app needs GPS and sensor access to track your biking speed and show your route on the map.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            Button(
                onClick = {
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BODY_SENSORS
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher?.launch(permissions.toTypedArray())
                },
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun SpeedTrackerScreen(
    locationManager: LocationManager,
    modifier: Modifier = Modifier,
    context: ComponentActivity,
    onSwitchToMap: () -> Unit = {}
) {
    val speed by locationManager.speedFlow.collectAsState()
    val gpsSpeed by locationManager.gpsSpeedFlow.collectAsState()
    val accuracy by locationManager.accuracyFlow.collectAsState()
    val isTracking by locationManager.isTracking.collectAsState()
    val sensorQuality by locationManager.sensorQualityFlow.collectAsState()
    val isSensorCalibrated by locationManager.isSensorCalibrated.collectAsState()
    
    var hasLocationPermission by remember {
        mutableStateOf(
            value = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true)
        )
    }
    
    var isStartingTracking by remember { mutableStateOf(value = false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    }
    
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && !isTracking && !isStartingTracking) {
            isStartingTracking = true
            val intent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isStartingTracking = false
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!hasLocationPermission) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Location & Sensor Permissions Required",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "This app needs GPS and sensor access to track your biking speed with enhanced accuracy.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            val permissions = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.BODY_SENSORS
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(permissions.toTypedArray())
                        }
                    ) {
                        Text("Grant Permissions")
                    }
                }
            } else {
                // Speed Display
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .background(
                            color = Color(0xFF1F1F1F),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", speed),
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FF00)
                        )
                        Text(
                            text = "km/h",
                            fontSize = 20.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                // Sensor Status & Info Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Sensor quality indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (sensorQuality == "Hybrid (GPS+Sensors)") 
                                    Color(0xFF00AA00) else Color(0xFF333333),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sensorQuality,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sensorQuality == "Hybrid (GPS+Sensors)") 
                                Color.Black else Color.White
                        )
                    }
                    
                    // GPS speed reference
                    Text(
                        "GPS Speed: ${String.format(Locale.US, "%.1f", gpsSpeed)} km/h",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    
                    // Sensor calibration status
                    Text(
                        if (isSensorCalibrated) "✓ Sensors Calibrated" else "⟳ Calibrating Sensors...",
                        fontSize = 12.sp,
                        color = if (isSensorCalibrated) Color(0xFF00FF00) else Color(0xFFFFAA00)
                    )
                    
                    // GPS accuracy
                    Text(
                        "Accuracy: ${String.format(Locale.US, "%.1f", accuracy)} m",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    
                    Text(
                        if (isTracking) "🟢 Tracking..." else "⚪ Not Tracking",
                        fontSize = 16.sp,
                        color = if (isTracking) Color(0xFF00FF00) else Color.Gray
                    )
                    
                    Text(
                        if (locationManager.isGPSEnabled()) "GPS: Enabled" else "GPS: Disabled",
                        fontSize = 14.sp,
                        color = if (locationManager.isGPSEnabled()) Color(0xFF00FF00) else Color(0xFFFF6B6B)
                    )
                }
                
                // Control Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(context, TrackingService::class.java)
                            if (isTracking) {
                                intent.action = TrackingService.ACTION_STOP
                            } else {
                                intent.action = TrackingService.ACTION_START
                            }
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = true
                    ) {
                        Text(if (isTracking) "Stop Tracking" else "Start Tracking")
                    }
                    
                    Button(
                        onClick = onSwitchToMap,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Map")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpeedTrackerPreview() {
    BikingTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .background(
                            color = Color(0xFF1F1F1F),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "24.5",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FF00)
                        )
                        Text(
                            text = "km/h",
                            fontSize = 20.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}