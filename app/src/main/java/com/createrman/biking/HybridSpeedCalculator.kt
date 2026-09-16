package com.createrman.biking

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Calculates hybrid speed by fusing GPS speed with accelerometer data.
 * Provides responsive speed estimates even when GPS signal is weak or unavailable.
 * 
 * Includes motion detection to avoid noise when stationary.
 */
class HybridSpeedCalculator {
    private val kalmanFilter = KalmanFilter(
        processNoise = 0.05f,     // More conservative - trust GPS more
        measurementNoise = 0.3f   // GPS is more reliable than we thought
    )
    
    // Exponential moving average for smoothing
    private var smoothedSpeed = 0.0f
    private val smoothingAlpha = 0.2f  // Lower = more smoothing, less jitter
    
    // Motion detection
    private var recentAccelerations = FloatArray(10)  // Last 10 acceleration samples
    private var accelIndex = 0
    private val motionThreshold = 0.2f  // m/s² - must exceed this to consider motion
    private val motionSamples = 5  // Must have N consecutive readings above threshold
    private var motionDetected = false
    private var motionSampleCount = 0
    
    private var lastGPSUpdateTime = System.currentTimeMillis()
    private var lastAccelUpdateTime = System.currentTimeMillis()
    
    /**
     * Update with GPS speed measurement.
     * @param speedMs GPS speed in m/s
     */
    fun updateGPSSpeed(speedMs: Float) {
        val currentTime = System.currentTimeMillis()
        
        // GPS speed is ground truth - always use it
        // But clamp to reasonable values
        val clampedSpeed = speedMs.coerceIn(0f, 50f)  // 0-180 km/h
        kalmanFilter.updateWithGPS(clampedSpeed)
        
        lastGPSUpdateTime = currentTime
    }
    
    /**
     * Update with accelerometer data for inter-GPS-update estimates.
     * @param forwardAcceleration Acceleration along forward axis in m/s²
     */
    fun updateAcceleration(forwardAcceleration: Float) {
        val currentTime = System.currentTimeMillis()
        val deltaTimeMs = (currentTime - lastAccelUpdateTime).toLong()
        val deltaTimeS = (deltaTimeMs / 1000.0f).coerceAtLeast(0.001f)
        
        // Store recent acceleration for motion detection
        recentAccelerations[accelIndex] = kotlin.math.abs(forwardAcceleration)
        accelIndex = (accelIndex + 1) % recentAccelerations.size
        
        // Check if we have continuous motion
        val avgRecentAccel = recentAccelerations.average().toFloat()
        
        if (avgRecentAccel > motionThreshold) {
            motionSampleCount++
            if (motionSampleCount >= motionSamples) {
                motionDetected = true
            }
        } else {
            motionSampleCount = 0
            motionDetected = false
        }
        
        // Only use acceleration if motion is clearly detected
        // This prevents noise from causing false speed changes
        if (motionDetected && kotlin.math.abs(forwardAcceleration) > 0.15f) {
            kalmanFilter.updateWithAcceleration(forwardAcceleration, deltaTimeS)
        } else if (!motionDetected) {
            // When stationary, clamp speed toward zero
            kalmanFilter.reduceSpeedTowardsZero(deltaTimeS)
        }
        
        lastAccelUpdateTime = currentTime
    }
    
    /**
     * Get current hybrid speed in m/s.
     * Combines Kalman-filtered estimates with exponential smoothing.
     */
    fun getHybridSpeedMs(): Float {
        val kalmanSpeed = kalmanFilter.getSpeed()
        
        // Apply exponential moving average for smoothing (heavy smoothing for stability)
        smoothedSpeed = smoothingAlpha * kalmanSpeed + (1 - smoothingAlpha) * smoothedSpeed
        
        return smoothedSpeed.coerceAtLeast(0f)
    }
    
    /**
     * Get current hybrid speed in km/h.
     */
    fun getHybridSpeedKmh(): Float {
        return getHybridSpeedMs() * 3.6f
    }
    
    /**
     * Detect if we're actually moving (vs noise).
     * Returns true if speed is above minimum threshold.
     */
    fun isMoving(): Boolean {
        return getHybridSpeedMs() > 0.3f  // ~1 km/h threshold
    }
    
    /**
     * Reset for new tracking session.
     */
    fun reset() {
        kalmanFilter.reset()
        smoothedSpeed = 0.0f
        recentAccelerations = FloatArray(10)
        accelIndex = 0
        motionDetected = false
        motionSampleCount = 0
        lastGPSUpdateTime = System.currentTimeMillis()
        lastAccelUpdateTime = System.currentTimeMillis()
    }
}
