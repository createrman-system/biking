package com.createrman.biking.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
    tableName = "ride_points",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("rideId")]
)
data class RidePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val speedMetersPerSecond: Float,
    val accuracyMeters: Float,
    val bearingDegrees: Float?,
    val timestampMillis: Long,
)
