package com.createrman.biking.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Query("SELECT * FROM rides ORDER BY startedAtMillis DESC")
    fun observeRides(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getRide(rideId: Long): RideEntity?

    @Query("SELECT * FROM ride_points WHERE rideId = :rideId ORDER BY timestampMillis ASC")
    suspend fun getPoints(rideId: Long): List<RidePointEntity>

    @Insert
    suspend fun insertRide(ride: RideEntity): Long

    @Insert
    suspend fun insertPoints(points: List<RidePointEntity>)

    @Update
    suspend fun updateRide(ride: RideEntity)

    @Transaction
    suspend fun insertRideWithPoints(ride: RideEntity, points: List<RidePointEntity>): Long {
        val rideId = insertRide(ride)
        insertPoints(points.map { it.copy(rideId = rideId) })
        return rideId
    }
}
