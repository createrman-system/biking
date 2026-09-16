package com.createrman.biking

import android.location.Location
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks route points and statistics for the bike ride.
 */
class RouteTracker {
    // List of all route points (latitude, longitude pairs)
    private val _routePointsFlow = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePointsFlow: StateFlow<List<Pair<Double, Double>>> = _routePointsFlow
    
    // Current location
    private val _currentLocationFlow = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocationFlow: StateFlow<Pair<Double, Double>?> = _currentLocationFlow
    
    // Statistics
    private val _distanceFlow = MutableStateFlow(0.0)
    val distanceFlow: StateFlow<Double> = _distanceFlow
    
    private val _durationFlow = MutableStateFlow(0L)
    val durationFlow: StateFlow<Long> = _durationFlow
    
    private val _maxSpeedFlow = MutableStateFlow(0.0f)
    val maxSpeedFlow: StateFlow<Float> = _maxSpeedFlow
    
    private val _avgSpeedFlow = MutableStateFlow(0.0f)
    val avgSpeedFlow: StateFlow<Float> = _avgSpeedFlow
    
    private var startTime = 0L
    private var totalDistance = 0.0
    private var speedSum = 0.0f
    private var speedSamples = 0
    private var lastPoint: Pair<Double, Double>? = null
    
    /**
     * Add a location point to the route.
     */
    fun addPoint(latitude: Double, longitude: Double, speed: Float = 0f) {
        val point = Pair(latitude, longitude)
        
        // Add to route
        val currentRoute = _routePointsFlow.value.toMutableList()
        currentRoute.add(point)
        _routePointsFlow.value = currentRoute
        
        // Update current location
        _currentLocationFlow.value = point
        
        // Calculate distance
        if (lastPoint != null) {
            val distance = calculateDistance(lastPoint!!, point)
            totalDistance += distance
            _distanceFlow.value = totalDistance
        }
        
        // Update statistics
        if (speed > 0) {
            if (speed > _maxSpeedFlow.value) {
                _maxSpeedFlow.value = speed
            }
            speedSum += speed
            speedSamples++
            _avgSpeedFlow.value = speedSum / speedSamples
        }
        
        // Update duration
        if (startTime == 0L) {
            startTime = System.currentTimeMillis()
        }
        _durationFlow.value = (System.currentTimeMillis() - startTime) / 1000
        
        lastPoint = point
    }
    
    /**
     * Calculate distance between two points in meters using Haversine formula.
     */
    private fun calculateDistance(point1: Pair<Double, Double>, point2: Pair<Double, Double>): Double {
        val earthRadiusKm = 6371.0
        
        val lat1Rad = Math.toRadians(point1.first)
        val lat2Rad = Math.toRadians(point2.first)
        val deltaLatRad = Math.toRadians(point2.first - point1.first)
        val deltaLonRad = Math.toRadians(point2.second - point1.second)
        
        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2) * sin(deltaLonRad / 2)
        
        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
        val distance = earthRadiusKm * c * 1000  // Convert to meters
        
        return distance
    }
    
    /**
     * Clear route and reset statistics.
     */
    fun reset() {
        _routePointsFlow.value = emptyList()
        _currentLocationFlow.value = null
        _distanceFlow.value = 0.0
        _durationFlow.value = 0L
        _maxSpeedFlow.value = 0f
        _avgSpeedFlow.value = 0f
        startTime = 0L
        totalDistance = 0.0
        speedSum = 0f
        speedSamples = 0
        lastPoint = null
    }
    
    /**
     * Get distance in kilometers.
     */
    fun getDistanceKm(): Double = _distanceFlow.value / 1000.0
    
    /**
     * Get formatted duration as HH:MM:SS.
     */
    fun getFormattedDuration(): String {
        val seconds = _durationFlow.value
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
}
