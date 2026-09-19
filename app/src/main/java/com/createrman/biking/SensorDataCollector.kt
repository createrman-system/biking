package com.createrman.biking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MotionSample(
    val forwardAcceleration: Float,
    val horizontalAccelerationMagnitude: Float,
    val confidence: Float,
    val timestampNanos: Long,
)

class SensorDataCollector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val rotationMatrix = FloatArray(9)
    private val gravity = FloatArray(3)
    private val rawAcceleration = FloatArray(3)
    private val worldLinearAcceleration = FloatArray(3)

    private var hasRotation = false
    private var hasLinearAcceleration = false
    private var lastBearingDegrees: Float? = null
    private var isListening = false

    private val _motionSample = MutableStateFlow(MotionSample(0f, 0f, 0f, 0L))
    val motionSample: StateFlow<MotionSample> = _motionSample

    private val _isCalibrated = MutableStateFlow(false)
    val isCalibrated: StateFlow<Boolean> = _isCalibrated

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                hasRotation = true
                _isCalibrated.value = true
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                rawAcceleration[0] = event.values[0]
                rawAcceleration[1] = event.values[1]
                rawAcceleration[2] = event.values[2]
                hasLinearAcceleration = true
                emitMotion(event.timestamp)
            }

            Sensor.TYPE_GRAVITY -> {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
                gravity[2] = event.values[2]
            }

            Sensor.TYPE_ACCELEROMETER -> {
                if (!hasLinearAcceleration) {
                    rawAcceleration[0] = event.values[0] - gravity[0]
                    rawAcceleration[1] = event.values[1] - gravity[1]
                    rawAcceleration[2] = event.values[2] - gravity[2]
                    emitMotion(event.timestamp)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun setCourseBearing(bearingDegrees: Float?) {
        lastBearingDegrees = bearingDegrees
    }

    private fun emitMotion(timestampNanos: Long) {
        if (!hasRotation) return

        worldLinearAcceleration[0] =
            rotationMatrix[0] * rawAcceleration[0] + rotationMatrix[1] * rawAcceleration[1] + rotationMatrix[2] * rawAcceleration[2]
        worldLinearAcceleration[1] =
            rotationMatrix[3] * rawAcceleration[0] + rotationMatrix[4] * rawAcceleration[1] + rotationMatrix[5] * rawAcceleration[2]
        worldLinearAcceleration[2] =
            rotationMatrix[6] * rawAcceleration[0] + rotationMatrix[7] * rawAcceleration[1] + rotationMatrix[8] * rawAcceleration[2]

        val east = worldLinearAcceleration[0]
        val north = worldLinearAcceleration[1]
        val horizontalMagnitude = sqrt(east * east + north * north)
        val bearing = lastBearingDegrees
        val forward = if (bearing != null) {
            val rad = Math.toRadians(bearing.toDouble())
            (east * sin(rad) + north * cos(rad)).toFloat()
        } else {
            // When bearing is unknown, we can't reliably determine forward motion.
            // Using horizontal magnitude directly causes positive drift from vibration.
            // We use a small fraction to represent "potential" motion, or 0 if very small.
            if (horizontalMagnitude < 0.15f) 0f else horizontalMagnitude * 0.5f
        }

        val tiltPenalty = (abs(worldLinearAcceleration[2]) / 9.81f).coerceIn(0f, 0.6f)
        val baseConfidence = when {
            bearing != null && hasLinearAcceleration -> 0.95f
            bearing != null -> 0.85f // Increased slightly
            hasLinearAcceleration -> 0.45f // Decreased to trust unknown-bearing sensors less
            else -> 0.25f
        }

        _motionSample.value = MotionSample(
            forwardAcceleration = forward.coerceIn(-8f, 8f),
            horizontalAccelerationMagnitude = horizontalMagnitude,
            confidence = (baseConfidence - tiltPenalty).coerceIn(0.1f, 1f),
            timestampNanos = timestampNanos,
        )
    }

    fun startListening() {
        if (isListening) return
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAcceleration?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (linearAcceleration == null) {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        isListening = true
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        hasRotation = false
        hasLinearAcceleration = false
        _isCalibrated.value = false
        _motionSample.value = MotionSample(0f, 0f, 0f, 0L)
    }

    fun getAccelerationMagnitude(): Float = _motionSample.value.horizontalAccelerationMagnitude

    fun getForwardAcceleration(): Float = _motionSample.value.forwardAcceleration
}
