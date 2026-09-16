package com.createrman.biking

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Composable for displaying OpenStreetMap with bike route.
 */
@Composable
fun MapScreen(
    routePoints: List<Pair<Double, Double>>,
    currentLocation: Pair<Double, Double>?,
    stats: RouteStats,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapView: MapView? by remember { mutableStateOf(null) }
    
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
                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Update route polyline
                view.overlays.clear()
                
                if (routePoints.size > 1) {
                    val polyline = Polyline(view)
                    polyline.setPoints(routePoints.map { GeoPoint(it.first, it.second) })
                    polyline.outlinePaint.color = android.graphics.Color.BLUE
                    polyline.outlinePaint.strokeWidth = 5f
                    view.overlays.add(polyline)
                    
                    // Center map on route
                    val minLat = routePoints.minOf { it.first }
                    val maxLat = routePoints.maxOf { it.first }
                    val minLon = routePoints.minOf { it.second }
                    val maxLon = routePoints.maxOf { it.second }
                    
                    val centerLat = (minLat + maxLat) / 2
                    val centerLon = (minLon + maxLon) / 2
                    
                    view.controller.setCenter(GeoPoint(centerLat, centerLon))
                    view.controller.setZoom(15.0)
                }
                
                // Add current location marker
                if (currentLocation != null) {
                    val marker = Marker(view)
                    marker.position = GeoPoint(currentLocation.first, currentLocation.second)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Current Location"
                    view.overlays.add(marker)
                }
                
                view.invalidate()
            }
        )
        
        // Stats Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Distance: ${String.format("%.2f", stats.distance)} km",
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                "Duration: ${stats.duration}",
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                "Speed: ${String.format("%.1f", stats.avgSpeed)} km/h avg / ${String.format("%.1f", stats.maxSpeed)} km/h max",
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                "Points: ${stats.pointCount}",
                color = Color.White,
                fontSize = 12.sp
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
    
    val stats = RouteStats(
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
            stats = stats,
            modifier = Modifier.fillMaxSize()
        )
        
        // Control buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSwitchToSpeedometer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Speedometer")
            }
            
            Button(
                onClick = onTrackingToggle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isTracking) "Stop Tracking" else "Start Tracking")
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
