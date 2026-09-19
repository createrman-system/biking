package com.createrman.biking.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RideEntity::class, RidePointEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BikingDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
}
