package com.createrman.biking

data class RouteStats(
    val currentSpeed: Float,     // km/h
    val distance: Double,        // kilometers
    val duration: String,        // formatted HH:MM:SS
    val maxSpeed: Float,         // km/h
    val avgSpeed: Float,         // km/h
    val pointCount: Int          // number of tracked points
)
