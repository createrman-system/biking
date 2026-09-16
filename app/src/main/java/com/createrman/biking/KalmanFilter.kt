package com.createrman.biking

/**
 * Simple 1D Kalman filter for fusing GPS speed with accelerometer-derived speed.
 * 
 * This filter combines:
 * - GPS speed: High accuracy but delayed (1-2 seconds latency)
 * - Accelerometer speed: Fast response but drifts over time
 */
class KalmanFilter(
    processNoise: Float = 0.05f,  // How fast speed can naturally change (m/s² variance)
    measurementNoise: Float = 0.5f  // GPS measurement uncertainty (m/s)
) {
    private var x = 0.0f  // State: current speed estimate (m/s)
    private var p = 1.0f  // State covariance: uncertainty in our estimate
    
    private val q = processNoise  // Process noise: how much we expect speed to change
    private val r = measurementNoise  // Measurement noise: GPS uncertainty
    
    /**
     * Update filter with a new GPS measurement.
     * @param gpsSpeed Speed from GPS in m/s
     */
    fun updateWithGPS(gpsSpeed: Float) {
        // Predict step
        p = p + q
        
        // Update step: incorporate GPS measurement
        val y = gpsSpeed - x  // Innovation: difference between measurement and prediction
        val s = p + r  // Innovation covariance
        val k = p / s  // Kalman gain
        
        x = x + k * y  // Update state
        p = (1 - k) * p  // Update covariance
    }
    
    /**
     * Update filter with accelerometer data to improve prediction between GPS updates.
     * @param acceleration Acceleration in m/s²
     * @param deltaTime Time since last update in seconds
     */
    fun updateWithAcceleration(acceleration: Float, deltaTime: Float) {
        // Simple integration: speed = previous_speed + acceleration * time
        val speedChange = acceleration * deltaTime
        
        // Predict step with motion model
        x = x + speedChange
        
        // Increase uncertainty since we're relying on noisy accelerometer
        p = p + q
        
        // Clamp speed to reasonable values (0 to 150 km/h = 0 to 41.7 m/s)
        x = x.coerceIn(0f, 41.7f)
    }
    
    /**
     * Reduce speed towards zero when stationary (no motion detected).
     * This prevents false speed estimates due to sensor noise.
     * @param deltaTime Time since last update in seconds
     */
    fun reduceSpeedTowardsZero(deltaTime: Float) {
        // Gently decay speed towards zero at ~1 m/s per second
        val decayRate = 1.0f  // m/s per second
        val speedReduction = decayRate * deltaTime
        
        x = (x - speedReduction).coerceAtLeast(0f)
        
        // Very low uncertainty when reducing to zero
        p = kotlin.math.max(0.01f, p * 0.95f)
    }
    
    /**
     * Get current speed estimate in m/s.
     */
    fun getSpeed(): Float = x
    
    /**
     * Reset the filter (useful when starting new tracking session).
     */
    fun reset() {
        x = 0.0f
        p = 1.0f
    }
}
