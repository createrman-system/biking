package com.createrman.biking.data.tracking

import com.createrman.biking.data.local.RideEntity
import com.createrman.biking.data.local.RidePointEntity
import com.createrman.biking.domain.model.RidePoint
import com.createrman.biking.domain.model.RideSummary

fun RideEntity.toSummary(): RideSummary = RideSummary(
    id = id,
    name = name,
    note = note,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    distanceMeters = distanceMeters,
    movingTimeSeconds = movingTimeSeconds,
    elapsedTimeSeconds = elapsedTimeSeconds,
    averageSpeedMetersPerSecond = averageSpeedMetersPerSecond,
    maxSpeedMetersPerSecond = maxSpeedMetersPerSecond,
    elevationGainMeters = elevationGainMeters,
    elevationLossMeters = elevationLossMeters,
)

fun RidePointEntity.toDomain(): RidePoint = RidePoint(
    latitude = latitude,
    longitude = longitude,
    elevationMeters = elevationMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    accuracyMeters = accuracyMeters,
    bearingDegrees = bearingDegrees,
    timestampMillis = timestampMillis,
)

fun RidePoint.toEntity(rideId: Long = 0): RidePointEntity = RidePointEntity(
    rideId = rideId,
    latitude = latitude,
    longitude = longitude,
    elevationMeters = elevationMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    accuracyMeters = accuracyMeters,
    bearingDegrees = bearingDegrees,
    timestampMillis = timestampMillis,
)
