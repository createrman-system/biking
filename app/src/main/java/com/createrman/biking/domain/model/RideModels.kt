package com.createrman.biking.domain.model

import java.time.Instant

data class RidePoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val speedMetersPerSecond: Float,
    val accuracyMeters: Float,
    val bearingDegrees: Float?,
    val timestampMillis: Long,
)

data class RideSummary(
    val id: Long,
    val name: String,
    val note: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val distanceMeters: Double,
    val movingTimeSeconds: Long,
    val elapsedTimeSeconds: Long,
    val averageSpeedMetersPerSecond: Float,
    val maxSpeedMetersPerSecond: Float,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
)

data class RideDetails(
    val summary: RideSummary,
    val points: List<RidePoint>,
)

enum class TrackingStatus {
    Idle,
    Tracking,
    Paused,
    AutoPaused,
}

enum class SpeedUnit(val label: String, val multiplier: Float) {
    Kmh("km/h", 3.6f),
    Mph("mph", 2.2369363f),
}

enum class AppThemeMode {
    System,
    Light,
    Dark,
}

data class TrackingSettings(
    val speedUnit: SpeedUnit = SpeedUnit.Kmh,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val autoPauseEnabled: Boolean = true,
    val autoPauseDelaySeconds: Int = 8,
)

data class TrackingUiState(
    val status: TrackingStatus = TrackingStatus.Idle,
    val speedMetersPerSecond: Float = 0f,
    val gpsSpeedMetersPerSecond: Float = 0f,
    val distanceMeters: Double = 0.0,
    val movingTimeSeconds: Long = 0L,
    val elapsedTimeSeconds: Long = 0L,
    val maxSpeedMetersPerSecond: Float = 0f,
    val averageSpeedMetersPerSecond: Float = 0f,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val bearingDegrees: Float? = null,
    val currentLocation: RidePoint? = null,
    val route: List<RidePoint> = emptyList(),
    val sensorMode: String = "GPS only",
    val isGpsEnabled: Boolean = false,
    val message: String? = null,
) {
    val startedAt: Instant?
        get() = route.firstOrNull()?.let { Instant.ofEpochMilli(it.timestampMillis) }
}
