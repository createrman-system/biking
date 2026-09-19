package com.createrman.biking

import kotlin.math.abs
import kotlin.math.max

/**
 * Adaptive one-dimensional Kalman filter for bike speed.
 *
 * State is speed in m/s. Prediction is driven by forward acceleration, while GPS
 * speed corrects accumulated drift. The filter adapts both process noise and
 * measurement noise from acceleration confidence and GPS accuracy.
 */
class KalmanFilter(
    private val baseProcessNoise: Float = 0.08f,
    private val baseMeasurementNoise: Float = 0.6f,
) {
    private var speed = 0f
    private var covariance = 4f

    fun predict(acceleration: Float, deltaTimeSeconds: Float, confidence: Float) {
        val dt = deltaTimeSeconds.coerceIn(0.001f, 1.5f)
        val trustedAcceleration = acceleration.coerceIn(-6f, 6f) * confidence.coerceIn(0f, 1f)
        speed = (speed + trustedAcceleration * dt).coerceIn(0f, MAX_BIKE_SPEED_MS)

        val maneuverNoise = baseProcessNoise + abs(trustedAcceleration) * 0.22f
        val confidencePenalty = 1f + (1f - confidence.coerceIn(0f, 1f)) * 4f
        covariance += maneuverNoise * confidencePenalty * dt
    }

    fun updateWithGps(gpsSpeed: Float, accuracyMeters: Float, hasBearing: Boolean) {
        val measurement = gpsSpeed.coerceIn(0f, MAX_BIKE_SPEED_MS)
        val accuracyNoise = when {
            accuracyMeters <= 0f -> baseMeasurementNoise * 2f
            accuracyMeters <= 5f -> baseMeasurementNoise
            accuracyMeters <= 15f -> baseMeasurementNoise * 2.5f
            else -> baseMeasurementNoise * 6f
        }
        val bearingBonus = if (hasBearing) 0.75f else 1f
        val measurementNoise = max(0.08f, accuracyNoise * bearingBonus)

        val innovation = measurement - speed
        val innovationCovariance = covariance + measurementNoise
        val gain = covariance / innovationCovariance
        speed = (speed + gain * innovation).coerceIn(0f, MAX_BIKE_SPEED_MS)
        covariance = max(0.001f, (1f - gain) * covariance)
    }

    fun forceStationary(deltaTimeSeconds: Float) {
        val decay = 2.5f * deltaTimeSeconds.coerceIn(0.001f, 1.5f) // Increased decay
        speed = (speed - decay).coerceAtLeast(0f)
        covariance = max(0.01f, covariance * 0.85f) // Tighten covariance more
    }

    fun resetCovariance(targetCovariance: Float = 0.5f) {
        covariance = targetCovariance
    }

    fun getSpeed(): Float = speed

    fun reset(initialSpeed: Float = 0f) {
        speed = initialSpeed.coerceIn(0f, MAX_BIKE_SPEED_MS)
        covariance = 4f
    }

    fun updateWithGPS(gpsSpeed: Float) {
        updateWithGps(gpsSpeed, accuracyMeters = 8f, hasBearing = false)
    }

    fun updateWithAcceleration(acceleration: Float, deltaTime: Float) {
        predict(acceleration, deltaTime, confidence = 0.65f)
    }

    fun reduceSpeedTowardsZero(deltaTime: Float) {
        forceStationary(deltaTime)
    }

    private companion object {
        const val MAX_BIKE_SPEED_MS = 45f
    }
}
