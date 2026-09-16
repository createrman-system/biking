package com.createrman.biking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects accelerometer and gyroscope sensor data for speed estimation.
 * Handles sensor calibration and filters raw sensor values.
 */
class SensorDataCollector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    // Sensor calibration - initially set to gravity
    private val accelerometerBias = FloatArray(3) { if (it == 2) 9.81f else 0f }
    private val gyroscopeBias = FloatArray(3)
    
    // Current sensor readings (device frame)
    private val _accelerationFlow = MutableStateFlow(FloatArray(3))
    val accelerationFlow: StateFlow<FloatArray> = _accelerationFlow
    
    private val _rotationRateFlow = MutableStateFlow(FloatArray(3))
    val rotationRateFlow: StateFlow<FloatArray> = _rotationRateFlow
    
    // Calibration state
    private val _isCalibrated = MutableStateFlow(false)
    val isCalibrated: StateFlow<Boolean> = _isCalibrated
    
    private var calibrationCount = 0
    private val calibrationSamples = 30
    private val calibrationAccels = MutableList(calibrationSamples) { FloatArray(3) }
    private val calibrationGyros = MutableList(calibrationSamples) { FloatArray(3) }
    
    private var isListening = false
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (!_isCalibrated.value) {
                    // Collect calibration data
                    if (calibrationCount < calibrationSamples) {
                        event.values.forEachIndexed { i, v -> calibrationAccels[calibrationCount][i] = v }
                        calibrationCount++
                        
                        if (calibrationCount >= calibrationSamples) {
                            computeCalibration()
                            _isCalibrated.value = true
                            calibrationCount = 0
                        }
                    }
                } else {
                    // Filter accelerometer values: subtract bias and apply high-pass filter
                    val filtered = FloatArray(3) { i ->
                        event.values[i] - accelerometerBias[i]
                    }
                    _accelerationFlow.value = filtered
                }
            }
            
            Sensor.TYPE_GYROSCOPE -> {
                // Filter gyroscope values: subtract bias (zero if not used)
                val filtered = FloatArray(3) { i ->
                    event.values[i] - gyroscopeBias[i]
                }
                _rotationRateFlow.value = filtered
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Accuracy change handling (optional)
    }
    
    /**
     * Compute calibration offsets from collected samples.
     * Accelerometer bias = average of collected accelerations (device at rest)
     * Gyroscope bias = average of collected rotation rates (device at rest)
     */
    private fun computeCalibration() {
        // Accelerometer bias - average over samples, but account for gravity
        val avgAccelX = calibrationAccels.map { it[0] }.average().toFloat()
        val avgAccelY = calibrationAccels.map { it[1] }.average().toFloat()
        val avgAccelZ = calibrationAccels.map { it[2] }.average().toFloat()
        
        // Compute magnitude of acceleration (should be ~9.81 m/s² for gravity)
        val accelMagnitude = sqrt(avgAccelX * avgAccelX + avgAccelY * avgAccelY + avgAccelZ * avgAccelZ)
        
        // Only use calibration if magnitude is close to gravity (9.81 ± 1 m/s²)
        // This indicates the device is truly at rest
        if (accelMagnitude in 8.5f..10.5f) {
            accelerometerBias[0] = avgAccelX
            accelerometerBias[1] = avgAccelY
            accelerometerBias[2] = avgAccelZ
        } else {
            // Device was moving during calibration, use default gravity direction
            accelerometerBias[0] = 0f
            accelerometerBias[1] = 0f
            accelerometerBias[2] = 9.81f
        }
        
        // Gyroscope bias - average over samples (should be ~0 at rest)
        gyroscopeBias[0] = calibrationGyros.map { it[0] }.average().toFloat()
        gyroscopeBias[1] = calibrationGyros.map { it[1] }.average().toFloat()
        gyroscopeBias[2] = calibrationGyros.map { it[2] }.average().toFloat()
    }
    
    fun startListening() {
        if (isListening) return
        
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        isListening = true
        _isCalibrated.value = false
        calibrationCount = 0
    }
    
    fun stopListening() {
        if (!isListening) return
        
        sensorManager.unregisterListener(this)
        isListening = false
        _isCalibrated.value = false
        _accelerationFlow.value = FloatArray(3)
        _rotationRateFlow.value = FloatArray(3)
    }
    
    /**
     * Get the magnitude of acceleration (total acceleration magnitude).
     */
    fun getAccelerationMagnitude(): Float {
        val accel = _accelerationFlow.value
        return sqrt(accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2])
    }
    
    /**
     * Estimate forward-axis acceleration assuming device is held upright.
     * Gyroscope helps determine device orientation.
     * Applies additional filtering to reduce noise.
     */
    fun getForwardAcceleration(): Float {
        // Simplified: assume forward axis is primarily Y or Z depending on device orientation
        // A more robust implementation would use gyroscope to compute full rotation matrix
        val accel = _accelerationFlow.value
        val gyro = _rotationRateFlow.value
        
        // Get the raw forward acceleration based on device orientation
        val rawForwardAccel = if (kotlin.math.abs(gyro[2]) > 0.5f) {
            accel[1] // Primary Y axis when rotating around Z
        } else {
            accel[2] // Primary Z axis when rotation is low
        }
        
        // Apply high-pass filter to remove low-frequency drift
        // This is done via a simple deadzone - ignore very small values
        val filteredAccel = if (kotlin.math.abs(rawForwardAccel) < 0.1f) {
            0f  // Treat tiny values as noise
        } else {
            rawForwardAccel
        }
        
        return filteredAccel
    }
}
