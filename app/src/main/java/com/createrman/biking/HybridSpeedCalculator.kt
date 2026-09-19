package com.createrman.biking

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fuses GPS speed and world-frame forward acceleration.
 */
class HybridSpeedCalculator(
    private val autoPauseDelaySeconds: Int = 8,
) {
    data class Result(
        val speedMetersPerSecond: Float,
        val isMoving: Boolean,
        val isAutoPaused: Boolean,
        val accelerationVariance: Float,
    )

    private val kalmanFilter = KalmanFilter()
    private val accelerationWindow = ArrayDeque<Float>()
    private var smoothedSpeed = 0f
    private var lastGpsSpeed = 0f
    private var lastUpdateNanos = 0L
    private var stationarySinceMillis: Long? = null
    private var autoPaused = false

    @Synchronized
    fun updateGpsSpeed(
        speedMetersPerSecond: Float,
        accuracyMeters: Float,
        hasBearing: Boolean,
        timestampMillis: Long = System.currentTimeMillis(),
    ): Result {
        lastGpsSpeed = speedMetersPerSecond.coerceIn(0f, 45f)
        kalmanFilter.updateWithGps(lastGpsSpeed, accuracyMeters, hasBearing)
        return publish(timestampMillis)
    }

    @Synchronized
    fun updateAcceleration(
        forwardAcceleration: Float,
        confidence: Float,
        timestampNanos: Long,
        timestampMillis: Long = System.currentTimeMillis(),
    ): Result {
        val dt = if (lastUpdateNanos == 0L) {
            0.02f
        } else {
            ((timestampNanos - lastUpdateNanos) / 1_000_000_000f).coerceIn(0.001f, 0.25f)
        }
        lastUpdateNanos = timestampNanos

        val filteredAcceleration = if (abs(forwardAcceleration) < 0.06f) 0f else forwardAcceleration // Increased deadzone
        pushAcceleration(filteredAcceleration)
        val variance = accelerationVariance()
        
        // HARD STATIONARY GATE: If GPS says we are stopped and vibration is low, FORCE speed to 0.
        val likelyStationary = lastGpsSpeed < 0.25f && variance < 0.045f && abs(filteredAcceleration) < 0.15f

        if (likelyStationary) {
            kalmanFilter.forceStationary(dt)
            if (variance < 0.02f) {
                kalmanFilter.reset(0f)
                kalmanFilter.resetCovariance(0.1f)
            }
        } else {
            // Outlier rejection: cycling acceleration rarely exceeds 4m/s^2 for long
            val clampedAccel = filteredAcceleration.coerceIn(-5f, 5f)
            kalmanFilter.predict(clampedAccel, dt, confidence)
        }
        return publish(timestampMillis)
    }

    private fun publish(timestampMillis: Long): Result {
        val rawSpeed = kalmanFilter.getSpeed()
        val variance = accelerationVariance()
        val moving = rawSpeed > 0.55f || lastGpsSpeed > 0.65f || variance > 0.08f

        if (moving) {
            stationarySinceMillis = null
            autoPaused = false
        } else {
            val since = stationarySinceMillis ?: timestampMillis.also { stationarySinceMillis = it }
            autoPaused = timestampMillis - since >= autoPauseDelaySeconds * 1000L
        }

        val alpha = if (moving) 0.32f else 0.65f // More aggressive pull to zero
        smoothedSpeed = if (autoPaused) {
            0f
        } else {
            val nextSpeed = alpha * rawSpeed + (1f - alpha) * smoothedSpeed
            // DISPLAY THRESHOLD: Show 0.0 if speed is < 1.0 km/h
            if (nextSpeed * 3.6f < 1.0f) 0f else nextSpeed
        }

        return Result(
            speedMetersPerSecond = smoothedSpeed.coerceAtLeast(0f),
            isMoving = moving,
            isAutoPaused = autoPaused,
            accelerationVariance = variance,
        )
    }

    private fun pushAcceleration(value: Float) {
        if (accelerationWindow.size == 40) accelerationWindow.removeFirst()
        accelerationWindow.addLast(value)
    }

    private fun accelerationVariance(): Float {
        if (accelerationWindow.size < 4) return 0f
        val mean = accelerationWindow.average().toFloat()
        return accelerationWindow.fold(0f) { acc, value ->
            val diff = value - mean
            acc + diff * diff
        } / max(1, accelerationWindow.size - 1)
    }

    @Synchronized
    fun getHybridSpeedMs(): Float {
        return smoothedSpeed.coerceAtLeast(0f)
    }

    fun getHybridSpeedKmh(): Float = getHybridSpeedMs() * 3.6f

    @Synchronized
    fun isMoving(): Boolean = getHybridSpeedMs() > 0.55f || lastGpsSpeed > 0.65f

    @Synchronized
    fun reset() {
        kalmanFilter.reset()
        accelerationWindow.clear()
        smoothedSpeed = 0f
        lastGpsSpeed = 0f
        lastUpdateNanos = 0L
        stationarySinceMillis = null
        autoPaused = false
    }

    fun updateGPSSpeed(speedMs: Float) {
        updateGpsSpeed(speedMs, accuracyMeters = 8f, hasBearing = false)
    }

    fun updateAcceleration(forwardAcceleration: Float) {
        updateAcceleration(forwardAcceleration, confidence = 0.6f, timestampNanos = System.nanoTime())
    }
}
