package com.createrman.biking

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.createrman.biking.domain.model.TrackingUiState
import com.createrman.biking.domain.model.TrackingStatus
import com.createrman.biking.domain.model.SpeedUnit
import java.util.Locale

/**
 * Constants for Map UI
 */
private const val BIKE_ICON_SIZE_DP = 40

/**
 * Composable for displaying OpenStreetMap with bike route.
 */
@Composable
fun MapScreen(
    state: TrackingUiState,
    unit: SpeedUnit,
    routePoints: List<Pair<Double, Double>>,
    currentLocation: Pair<Double, Double>?,
    bearingDegrees: Float? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val mapView = remember { MapView(context) }
    
    // Create scaled bike icon
    val bikeIconBitmap = remember(context) {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_bike)
        val sizePx = (BIKE_ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        drawable?.toBitmap(width = sizePx, height = sizePx)
    }
    
    val locationOverlay = remember(mapView, bikeIconBitmap) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
            setDrawAccuracyEnabled(false) // Disable large accuracy circle
            
            if (bikeIconBitmap != null) {
                setPersonIcon(bikeIconBitmap)
                setDirectionArrow(bikeIconBitmap, bikeIconBitmap)
                setPersonHotspot(bikeIconBitmap.width / 2f, bikeIconBitmap.height / 2f)
            }
        }
    }
    
    var followUser by remember { mutableStateOf(true) }
    
    // Initialize osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))
        
        // Use currentLocation for immediate centering if available
        if (currentLocation != null) {
            mapView.controller.setCenter(GeoPoint(currentLocation.first, currentLocation.second))
            mapView.controller.setZoom(17.5)
        }

        locationOverlay.runOnFirstFix {
            mapView.post {
                mapView.controller.setZoom(17.5)
                mapView.controller.animateTo(locationOverlay.myLocation)
            }
        }
    }
    
    // Handle Lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    locationOverlay.enableMyLocation()
                    if (followUser) locationOverlay.enableFollowLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    locationOverlay.disableMyLocation()
                    mapView.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Map View
        AndroidView(
            factory = { 
                mapView.apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    
                    // Add listener to disable follow mode when user pans manually
                    addMapListener(DelayedMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean = false
                        override fun onZoom(event: ZoomEvent?): Boolean = false
                    }, 100))
                    
                    // Simple gesture detection to disable followUser
                    setOnTouchListener { _, _ ->
                        if (followUser) {
                            followUser = false
                            locationOverlay.disableFollowLocation()
                        }
                        false
                    }
                    
                    overlays.add(locationOverlay)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Update route line
                
                // Clear all except MyLocation overlay
                val toRemove = view.overlays.filter { it != locationOverlay }
                view.overlays.removeAll(toRemove)
                
                // Add Polyline FIRST so it is UNDER the location overlay
                if (routePoints.size > 1) {
                    val polyline = Polyline(view)
                    polyline.setPoints(routePoints.map { GeoPoint(it.first, it.second) })
                    polyline.outlinePaint.color = "#FF9800".toColorInt() // Orange
                    polyline.outlinePaint.strokeWidth = 10f
                    view.overlays.add(0, polyline) // Insert at bottom
                }
                
                // Ensure locationOverlay is present and on top
                if (!view.overlays.contains(locationOverlay)) {
                    view.overlays.add(locationOverlay)
                }
                
                // Sync follow state
                if (followUser && !locationOverlay.isFollowLocationEnabled) {
                    locationOverlay.enableFollowLocation()
                } else if (!followUser && locationOverlay.isFollowLocationEnabled) {
                    locationOverlay.disableFollowLocation()
                }
                
                view.invalidate()
            }
        )
        
        // Floating Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp, bottom = 180.dp), // Lift up FABs to not overlap stats
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FloatingActionButton(
                onClick = { followUser = !followUser },
                containerColor = if (followUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (followUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Follow Me"
                )
            }
            FloatingActionButton(
                onClick = { followUser = false },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOutMap,
                    contentDescription = "Fit Route"
                )
            }
        }

        // Stats Overlay
        if (state.status != TrackingStatus.Idle) {
            val stats = RouteStats(
                currentSpeed = state.speedMetersPerSecond * unit.multiplier,
                distance = state.distanceMeters / 1000.0,
                duration = formatDuration(state.movingTimeSeconds),
                maxSpeed = state.maxSpeedMetersPerSecond * unit.multiplier,
                avgSpeed = state.averageSpeedMetersPerSecond * unit.multiplier,
                pointCount = routePoints.size
            )
            StatsCard(
                stats = stats,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}

@Composable
fun StatsCard(stats: RouteStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current Speed Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(min = 80.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", stats.currentSpeed),
                    fontSize = 44.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "KM/H",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            VerticalDivider(modifier = Modifier.height(60.dp).padding(horizontal = 8.dp))
            
            // Center stats
            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatRow(icon = Icons.Default.Timeline, label = "Dist", value = String.format(Locale.US, "%.2f km", stats.distance), isCompact = true)
                StatRow(icon = Icons.Default.Speed, label = "Avg", value = String.format(Locale.US, "%.1f km/h", stats.avgSpeed), isCompact = true)
            }
            
            // Right stats
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stats.duration,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "Max: ${String.format(Locale.US, "%.1f", stats.maxSpeed)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun StatRow(icon: ImageVector, label: String, value: String, isCompact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (isCompact) 16.dp else 20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = if (isCompact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MapScreenPreview() {
    MaterialTheme {
        val stats = RouteStats(
            currentSpeed = 25.4f,
            distance = 12.5,
            duration = "00:45:12",
            maxSpeed = 35.2f,
            avgSpeed = 22.1f,
            pointCount = 150
        )
        Box(modifier = Modifier.fillMaxSize()) {
            MapScreen(
                state = TrackingUiState(),
                unit = SpeedUnit.Kmh,
                routePoints = listOf(
                    Pair(52.5200, 13.4050),
                    Pair(52.5210, 13.4060)
                ),
                currentLocation = Pair(52.5210, 13.4060),
                modifier = Modifier.fillMaxSize()
            )
            StatsCard(
                stats = stats,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Full screen with map and controls.
 */
@Composable
fun MapScreenWithControls(
    locationManager: LocationManager,
    isTracking: Boolean,
    onTrackingToggle: () -> Unit,
    onSwitchToSpeedometer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val routePoints by locationManager.routePointsFlow.collectAsState()
    val currentLocation by locationManager.currentLocationFlow.collectAsState()
    val distance by locationManager.distanceFlow.collectAsState()
    val duration by locationManager.durationFlow.collectAsState()
    val maxSpeed by locationManager.maxSpeedFlow.collectAsState()
    val avgSpeed by locationManager.avgSpeedFlow.collectAsState()
    val currentSpeed by locationManager.speedFlow.collectAsState()
    
    // stats variable removed as it's now handled inside MapScreen
    
    Box(modifier = modifier.fillMaxSize()) {
        MapScreen(
            state = TrackingUiState(
                status = if (isTracking) TrackingStatus.Tracking else TrackingStatus.Idle,
                speedMetersPerSecond = currentSpeed / 3.6f,
                distanceMeters = distance,
                movingTimeSeconds = duration,
                maxSpeedMetersPerSecond = maxSpeed / 3.6f,
                averageSpeedMetersPerSecond = avgSpeed / 3.6f
            ),
            unit = SpeedUnit.Kmh,
            routePoints = routePoints,
            currentLocation = currentLocation,
            modifier = Modifier.fillMaxSize()
        )
        
        // Controls at the bottom (StatsCard is now inside MapScreen)
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSwitchToSpeedometer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Speedometer")
                    }
                    
                    Button(
                        onClick = onTrackingToggle,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isTracking) "Stop" else "Start")
                    }
                }
            }
        }
    }
}

/**
 * Format duration in seconds to HH:MM:SS string.
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}
