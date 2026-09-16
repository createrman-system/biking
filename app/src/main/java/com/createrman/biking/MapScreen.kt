package com.createrman.biking

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
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
import java.util.Locale

/**
 * Composable for displaying OpenStreetMap with bike route.
 */
@Composable
fun MapScreen(
    routePoints: List<Pair<Double, Double>>,
    currentLocation: Pair<Double, Double>?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var followUser by remember { mutableStateOf(true) }
    var isFirstLocation by remember { mutableStateOf(true) }
    
    // Initialize osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Map View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    
                    // Add listener to disable follow mode when user pans manually
                    addMapListener(DelayedMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean = false
                    }, 100))
                    
                    // Simple gesture detection to disable followUser
                    setOnTouchListener { _, _ ->
                        if (followUser) {
                            followUser = false
                        }
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.overlays.clear()
                
                // Route line
                if (routePoints.size > 1) {
                    val polyline = Polyline(view)
                    polyline.setPoints(routePoints.map { GeoPoint(it.first, it.second) })
                    polyline.outlinePaint.color = android.graphics.Color.BLUE
                    polyline.outlinePaint.strokeWidth = 8f
                    view.overlays.add(polyline)
                }
                
                // Current location marker
                if (currentLocation != null) {
                    val currentPoint = GeoPoint(currentLocation.first, currentLocation.second)
                    val marker = Marker(view)
                    marker.position = currentPoint
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    
                    // Set bike icon
                    val bikeIcon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_bike)
                    if (bikeIcon != null) {
                        marker.icon = bikeIcon
                    }
                    
                    marker.title = "You are here"
                    view.overlays.add(marker)
                    
                    if (followUser) {
                        if (isFirstLocation) {
                            view.controller.setCenter(currentPoint)
                            view.controller.setZoom(19.0)
                            isFirstLocation = false
                        } else {
                            view.controller.animateTo(currentPoint)
                            if (view.zoomLevelDouble < 17.0) {
                                view.controller.setZoom(19.0)
                            }
                        }
                    }
                }
                
                view.invalidate()
            }
        )
        
        // Floating Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
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
        }
    }
}

@Composable
fun StatsCard(stats: RouteStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Current Speed Section - Much Bigger
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "%.1f", stats.currentSpeed),
                        fontSize = 64.sp,
                        lineHeight = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "KM/H",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                VerticalDivider(modifier = Modifier.height(80.dp).padding(horizontal = 16.dp))
                
                // Other stats
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatRow(icon = Icons.Default.Timeline, label = "Distance", value = String.format(Locale.US, "%.2f km", stats.distance), isLarge = true)
                    StatRow(icon = Icons.Default.Speed, label = "Avg Speed", value = String.format(Locale.US, "%.1f km/h", stats.avgSpeed), isLarge = true)
                }
                
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stats.duration,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Max: ${String.format(Locale.US, "%.1f", stats.maxSpeed)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun StatRow(icon: ImageVector, label: String, value: String, isLarge: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (isLarge) 20.dp else 14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                fontSize = if (isLarge) 16.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    
    val stats = RouteStats(
        currentSpeed = currentSpeed,
        distance = distance / 1000.0,
        duration = formatDuration(duration),
        maxSpeed = maxSpeed,
        avgSpeed = avgSpeed,
        pointCount = routePoints.size
    )
    
    Box(modifier = modifier.fillMaxSize()) {
        MapScreen(
            routePoints = routePoints,
            currentLocation = currentLocation,
            modifier = Modifier.fillMaxSize()
        )
        
        // Stats and controls at the bottom
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            StatsCard(stats)
            
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
