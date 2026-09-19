package com.createrman.biking.data.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.createrman.biking.HybridSpeedCalculator
import com.createrman.biking.SensorDataCollector
import com.createrman.biking.data.local.RideDao
import com.createrman.biking.data.local.RideEntity
import com.createrman.biking.data.settings.SettingsRepository
import com.createrman.biking.domain.model.RideDetails
import com.createrman.biking.domain.model.RidePoint
import com.createrman.biking.domain.model.RideSummary
import com.createrman.biking.domain.model.TrackingStatus
import com.createrman.biking.domain.model.TrackingUiState
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrackingRepository(
    private val context: Context,
    private val sensorCollector: SensorDataCollector,
    private val rideDao: RideDao,
    private val settingsRepository: SettingsRepository,
    private val gpxExporter: GpxExporter,
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sensorJob: Job? = null
    private var settingsJob: Job? = null
    @Volatile
    private var calculator = HybridSpeedCalculator()

    private val route = mutableListOf<RidePoint>()
    private val _uiState = MutableStateFlow(TrackingUiState(isGpsEnabled = isGpsEnabled()))
    val uiState: StateFlow<TrackingUiState> = _uiState
    val rides: Flow<List<RideSummary>> = rideDao.observeRides().map { list -> list.map { it.toSummary() } }

    private var startedAtMillis = 0L
    private var activeMovingStartedAt = 0L
    private var accumulatedMovingTimeMillis = 0L
    private var lastAcceptedPoint: RidePoint? = null
    private var distanceMeters = 0.0
    private var speedSum = 0.0
    private var speedSamples = 0
    private var maxSpeed = 0f
    private var elevationGain = 0.0
    private var elevationLoss = 0.0
    private var filteredElevation: Double? = null
    private var manualPaused = false
    private var currentName = ""
    private var currentNote = ""

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = handleLocation(location)
        override fun onProviderEnabled(provider: String) = updateGpsAvailability()
        override fun onProviderDisabled(provider: String) = updateGpsAvailability()
        @Deprecated("Deprecated in Android SDK")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    init {
        settingsJob = scope.launch {
            settingsRepository.settings.collectLatest { settings ->
                calculator = HybridSpeedCalculator(settings.autoPauseDelaySeconds)
            }
        }
    }

    @Synchronized
    fun start(name: String = "", note: String = "") {
        if (_uiState.value.status != TrackingStatus.Idle) return
        if (!hasLocationPermission()) {
            _uiState.value = _uiState.value.copy(message = "Location permission is required")
            return
        }
        resetSession(name, note)
        startedAtMillis = System.currentTimeMillis()
        activeMovingStartedAt = startedAtMillis
        manualPaused = false
        calculator.reset()
        sensorCollector.startListening()
        locationManager.requestLocationUpdates(AndroidLocationManager.GPS_PROVIDER, 500L, 0f, locationListener)
        
        // Ensure UI state starts at clean zero
        _uiState.value = _uiState.value.copy(
            status = TrackingStatus.Tracking,
            speedMetersPerSecond = 0f,
            gpsSpeedMetersPerSecond = 0f,
            distanceMeters = 0.0,
            movingTimeSeconds = 0L,
            elapsedTimeSeconds = 0L,
            maxSpeedMetersPerSecond = 0f,
            averageSpeedMetersPerSecond = 0f,
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            message = null
        )

        sensorJob = scope.launch {
            sensorCollector.motionSample.collectLatest { sample ->
                if (sample.timestampNanos == 0L || manualPaused) return@collectLatest
                val result = calculator.updateAcceleration(
                    forwardAcceleration = sample.forwardAcceleration,
                    confidence = sample.confidence,
                    timestampNanos = sample.timestampNanos,
                )
                applyFusionResult(result)
            }
        }
    }

    @Synchronized
    fun pause(manual: Boolean = true) {
        val state = _uiState.value.status
        if (state == TrackingStatus.Tracking || state == TrackingStatus.AutoPaused) {
            closeMovingSegment()
            manualPaused = manual
            _uiState.value = _uiState.value.copy(status = TrackingStatus.Paused, speedMetersPerSecond = 0f)
        }
    }

    @Synchronized
    fun resume() {
        if (_uiState.value.status != TrackingStatus.Paused) return
        manualPaused = false
        activeMovingStartedAt = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(status = TrackingStatus.Tracking)
    }

    suspend fun stopAndSave(name: String = currentName, note: String = currentNote): Long? {
        val data = synchronized(this) {
            if (_uiState.value.status == TrackingStatus.Idle) {
                null
            } else {
                closeMovingSegment()
                val endedAt = System.currentTimeMillis()
                val summary = buildRideEntity(
                    name = name.ifBlank { "Ride ${java.time.LocalDate.now()}" },
                    note = note,
                    endedAt = endedAt,
                )
                val pointsEntities = route.map { it.toEntity() }
                stopSensorsAndLocation()
                Pair(summary, pointsEntities)
            }
        } ?: return null
        
        val (summary, points) = data
        val rideId = withContext(Dispatchers.IO) { rideDao.insertRideWithPoints(summary, points) }
        _uiState.value = TrackingUiState(isGpsEnabled = isGpsEnabled(), message = "Ride saved")
        return rideId
    }

    @Synchronized
    fun stopWithoutSaving() {
        closeMovingSegment()
        stopSensorsAndLocation()
        _uiState.value = TrackingUiState(isGpsEnabled = isGpsEnabled())
    }

    @Synchronized
    fun updateRideMetadata(name: String, note: String) {
        currentName = name
        currentNote = note
    }

    suspend fun getRideDetails(rideId: Long): RideDetails? = withContext(Dispatchers.IO) {
        val ride = rideDao.getRide(rideId) ?: return@withContext null
        RideDetails(ride.toSummary(), rideDao.getPoints(rideId).map { it.toDomain() })
    }

    suspend fun exportRide(rideId: Long): File? {
        val details = getRideDetails(rideId) ?: return null
        return withContext(Dispatchers.IO) { gpxExporter.export(details) }
    }

    fun isGpsEnabled(): Boolean = locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)

    @Synchronized
    private fun handleLocation(location: Location) {
        val bearing = when {
            location.hasBearing() -> location.bearing
            lastAcceptedPoint != null -> bearingBetween(lastAcceptedPoint!!, location)
            else -> null
        }
        sensorCollector.setCourseBearing(bearing)
        val gpsSpeed = if (location.hasSpeed()) location.speed else 0f
        val fusion = calculator.updateGpsSpeed(
            speedMetersPerSecond = gpsSpeed,
            accuracyMeters = location.accuracy,
            hasBearing = bearing != null,
            timestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        applyFusionResult(fusion, gpsSpeed)
        val status = _uiState.value.status
        if (status == TrackingStatus.Paused || status == TrackingStatus.AutoPaused) return

        val point = RidePoint(
            latitude = location.latitude,
            longitude = location.longitude,
            elevationMeters = smoothElevation(location),
            speedMetersPerSecond = fusion.speedMetersPerSecond,
            accuracyMeters = location.accuracy,
            bearingDegrees = bearing,
            timestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        addRoutePoint(point)
    }

    @Synchronized
    private fun applyFusionResult(result: HybridSpeedCalculator.Result, gpsSpeed: Float = _uiState.value.gpsSpeedMetersPerSecond) {
        val now = System.currentTimeMillis()
        val autoPause = result.isAutoPaused && !manualPaused
        val previousStatus = _uiState.value.status
        val nextStatus = when {
            previousStatus == TrackingStatus.Idle -> TrackingStatus.Idle
            manualPaused -> TrackingStatus.Paused
            autoPause -> TrackingStatus.AutoPaused
            else -> TrackingStatus.Tracking
        }
        if (nextStatus == TrackingStatus.AutoPaused && previousStatus == TrackingStatus.Tracking) closeMovingSegment(now)
        if (nextStatus == TrackingStatus.Tracking && previousStatus == TrackingStatus.AutoPaused) activeMovingStartedAt = now
        val movingTime = currentMovingTimeMillis(now) / 1000
        _uiState.value = _uiState.value.copy(
            status = nextStatus,
            speedMetersPerSecond = if (nextStatus == TrackingStatus.AutoPaused) 0f else result.speedMetersPerSecond,
            gpsSpeedMetersPerSecond = gpsSpeed,
            movingTimeSeconds = movingTime,
            elapsedTimeSeconds = elapsedTimeSeconds(now),
            sensorMode = if (sensorCollector.isCalibrated.value) "Hybrid GPS + sensors" else "GPS primary",
            isGpsEnabled = isGpsEnabled(),
        )
    }

    @Synchronized
    private fun addRoutePoint(point: RidePoint) {
        val last = lastAcceptedPoint
        if (last != null) {
            val segment = distanceBetween(last.latitude, last.longitude, point.latitude, point.longitude)
            if (segment > 0.4 && point.accuracyMeters <= 40f) distanceMeters += segment
            val elevationDelta = (point.elevationMeters ?: filteredElevation ?: 0.0) - (last.elevationMeters ?: filteredElevation ?: 0.0)
            if (elevationDelta > 1.2) elevationGain += elevationDelta
            if (elevationDelta < -1.2) elevationLoss += -elevationDelta
        }
        route.add(point)
        lastAcceptedPoint = point
        if (point.speedMetersPerSecond > 0.3f) {
            speedSamples++
            speedSum += point.speedMetersPerSecond
            maxSpeed = maxOf(maxSpeed, point.speedMetersPerSecond)
        }
        _uiState.value = _uiState.value.copy(
            currentLocation = point,
            route = route.toList(),
            distanceMeters = distanceMeters,
            maxSpeedMetersPerSecond = maxSpeed,
            averageSpeedMetersPerSecond = if (speedSamples == 0) 0f else (speedSum / speedSamples).toFloat(),
            elevationGainMeters = elevationGain,
            elevationLossMeters = elevationLoss,
            accuracyMeters = point.accuracyMeters,
            bearingDegrees = point.bearingDegrees,
        )
    }

    private fun smoothElevation(location: Location): Double? {
        if (!location.hasAltitude()) return filteredElevation
        val altitude = location.altitude
        filteredElevation = filteredElevation?.let { it * 0.82 + altitude * 0.18 } ?: altitude
        return filteredElevation
    }

    private fun resetSession(name: String, note: String) {
        route.clear()
        lastAcceptedPoint = null
        distanceMeters = 0.0
        speedSum = 0.0
        speedSamples = 0
        maxSpeed = 0f
        elevationGain = 0.0
        elevationLoss = 0.0
        filteredElevation = null
        accumulatedMovingTimeMillis = 0L
        startedAtMillis = 0L
        currentName = name
        currentNote = note
    }

    private fun buildRideEntity(name: String, note: String, endedAt: Long): RideEntity {
        val movingSeconds = (accumulatedMovingTimeMillis / 1000.0).roundToLong()
        return RideEntity(
            name = name,
            note = note,
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAt,
            distanceMeters = distanceMeters,
            movingTimeSeconds = movingSeconds,
            elapsedTimeSeconds = elapsedTimeSeconds(endedAt),
            averageSpeedMetersPerSecond = if (speedSamples == 0) 0f else (speedSum / speedSamples).toFloat(),
            maxSpeedMetersPerSecond = maxSpeed,
            elevationGainMeters = elevationGain,
            elevationLossMeters = elevationLoss,
        )
    }

    private fun closeMovingSegment(now: Long = System.currentTimeMillis()) {
        if (activeMovingStartedAt > 0L) {
            accumulatedMovingTimeMillis += (now - activeMovingStartedAt).coerceAtLeast(0L)
            activeMovingStartedAt = 0L
        }
    }

    private fun currentMovingTimeMillis(now: Long): Long {
        val openSegment = if (_uiState.value.status == TrackingStatus.Tracking && activeMovingStartedAt > 0L) {
            now - activeMovingStartedAt
        } else {
            0L
        }
        return accumulatedMovingTimeMillis + openSegment.coerceAtLeast(0L)
    }

    private fun elapsedTimeSeconds(now: Long): Long = if (startedAtMillis == 0L) 0L else (now - startedAtMillis) / 1000

    private fun stopSensorsAndLocation() {
        sensorJob?.cancel()
        sensorJob = null
        runCatching { locationManager.removeUpdates(locationListener) }
        sensorCollector.stopListening()
        calculator.reset()
        // startedAtMillis is reset in resetSession, but we keep activeMovingStartedAt reset here
        activeMovingStartedAt = 0L
        manualPaused = false
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateGpsAvailability() {
        _uiState.value = _uiState.value.copy(isGpsEnabled = isGpsEnabled())
    }

    private fun bearingBetween(from: RidePoint, to: Location): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val lonDelta = Math.toRadians(to.longitude - from.longitude)
        val y = sin(lonDelta) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lonDelta)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
