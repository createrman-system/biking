package com.createrman.biking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridSpeedCalculatorTest {
    @Test
    fun accelerationAndGpsProduceMovingSpeed() {
        val calculator = HybridSpeedCalculator(autoPauseDelaySeconds = 5)

        calculator.updateGpsSpeed(4f, accuracyMeters = 4f, hasBearing = true, timestampMillis = 1_000L)
        repeat(20) { index ->
            calculator.updateAcceleration(
                forwardAcceleration = 0.3f,
                confidence = 0.9f,
                timestampNanos = (index + 1) * 20_000_000L,
                timestampMillis = 1_000L + index * 20L,
            )
        }

        assertTrue(calculator.getHybridSpeedMs() > 2f)
        assertTrue(calculator.isMoving())
    }

    @Test
    fun stableStationarySamplesAutoPause() {
        val calculator = HybridSpeedCalculator(autoPauseDelaySeconds = 1)
        var result = calculator.updateGpsSpeed(0f, accuracyMeters = 3f, hasBearing = true, timestampMillis = 1_000L)

        repeat(90) { index ->
            result = calculator.updateAcceleration(
                forwardAcceleration = 0.01f,
                confidence = 0.9f,
                timestampNanos = (index + 1) * 20_000_000L,
                timestampMillis = 1_000L + index * 20L,
            )
        }

        assertFalse(result.isMoving)
        assertTrue(result.isAutoPaused)
        assertTrue(calculator.getHybridSpeedMs() < 0.2f)
    }
}
