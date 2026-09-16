package com.createrman.biking

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages location tracking and hybrid speed estimation.
 * Fuses GPS speed with accelerometer/gyroscope data for responsive, accurate speed tracking.
 * Also tracks route points for map visualization.
 */
class LocationManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    
    // Hybrid speed system
    private val sensorCollector = SensorDataCollector(appContext)
    private val hybridCalculator = HybridSpeedCalculator()
    
    // Route tracking
    private val routeTracker = RouteTracker()
    
    companion object {
        @Volatile
        private var INSTANCE: LocationManager? = null

        fun getInstance(context: Context): LocationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationManager(context).also { INSTANCE = it }
            }
        }
    }
    val routePointsFlow: StateFlow<List<Pair<Double, Double>>> = routeTracker.routePointsFlow
    val currentLocationFlow: StateFlow<Pair<Double, Double>?> = routeTracker.currentLocationFlow
    val distanceFlow: StateFlow<Double> = routeTracker.distanceFlow
    val durationFlow: StateFlow<Long> = routeTracker.durationFlow
    val maxSpeedFlow: StateFlow<Float> = routeTracker.maxSpeedFlow
    val avgSpeedFlow: StateFlow<Float> = routeTracker.avgSpeedFlow
    
    // GPS-only speed (for reference)
    private val _gpsSpeedFlow = MutableStateFlow(0.0f)
    val gpsSpeedFlow: StateFlow<Float> = _gpsSpeedFlow
    
    // Hybrid speed (GPS + sensors)
    private val _speedFlow = MutableStateFlow(0.0f)
    val speedFlow: StateFlow<Float> = _speedFlow
    
    private val _accuracyFlow = MutableStateFlow(0.0f)
    val accuracyFlow: StateFlow<Float> = _accuracyFlow
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking
    
    // Sensor quality indicator
    private val _sensorQualityFlow = MutableStateFlow("GPS-Only")
    val sensorQualityFlow: StateFlow<String> = _sensorQualityFlow
    
    private val _isSensorCalibrated = MutableStateFlow(false)
    val isSensorCalibrated: StateFlow<Boolean> = _isSensorCalibrated
    
    private val scope = CoroutineScope(Dispatchers.Default)
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Get GPS speed in m/s
            val gpsSpeedMs = if (location.hasSpeed()) {
                location.speed
            } else {
                0.0f
            }
            
            // Store GPS speed for reference
            _gpsSpeedFlow.value = gpsSpeedMs * 3.6f
            
            // Update hybrid calculator with GPS data
            hybridCalculator.updateGPSSpeed(gpsSpeedMs)
            
            // Get hybrid speed and update UI
            val hybridSpeedKmh = hybridCalculator.getHybridSpeedKmh()
            _speedFlow.value = hybridSpeedKmh
            _accuracyFlow.value = location.accuracy
            
            // Track route point
            routeTracker.addPoint(location.latitude, location.longitude, hybridSpeedKmh)
            
            // Update sensor quality indicator
            val quality = if (sensorCollector.isCalibrated.value) {
                "Hybrid (GPS+Sensors)"
            } else {
                "GPS-Primary"
            }
            _sensorQualityFlow.value = quality
        }
        
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    
    fun startTracking() {
        try {
            // Start GPS tracking
            locationManager.requestLocationUpdates(
                AndroidLocationManager.GPS_PROVIDER,
                100, // Update every 100 milliseconds
                0f, // Update even if distance hasn't changed
                locationListener
            )
            
            // Start sensor tracking
            sensorCollector.startListening()
            hybridCalculator.reset()
            
            // Launch coroutine to process sensor data
            scope.launch {
                while (_isTracking.value) {
                    val accel = sensorCollector.getForwardAcceleration()
                    hybridCalculator.updateAcceleration(accel)
                    
                    // Update speed and sensor status on UI thread
                    scope.launch(Dispatchers.Main) {
                        _speedFlow.value = hybridCalculator.getHybridSpeedKmh()
                        _isSensorCalibrated.value = sensorCollector.isCalibrated.value
                    }
                    
                    // Process sensor data at ~50 Hz
                    Thread.sleep(20)
                }
            }
            
            _isTracking.value = true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    
    fun stopTracking() {
        _speedFlow.value = 0.0f
        _gpsSpeedFlow.value = 0.0f
        _accuracyFlow.value = 0.0f
        _isTracking.value = false
        _sensorQualityFlow.value = "Stopped"
        _isSensorCalibrated.value = false
        
        try {
            locationManager.removeUpdates(locationListener)
            sensorCollector.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun isGPSEnabled(): Boolean {
        return locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)
    }
    
    /**
     * Get route statistics.
     */
    fun getRouteStats(): RouteStats {
        return RouteStats(
            currentSpeed = _speedFlow.value,
            distance = routeTracker.getDistanceKm(),
            duration = routeTracker.getFormattedDuration(),
            maxSpeed = maxSpeedFlow.value,
            avgSpeed = avgSpeedFlow.value,
            pointCount = routePointsFlow.value.size
        )
    }
}
