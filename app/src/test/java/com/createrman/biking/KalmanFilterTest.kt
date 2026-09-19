package com.createrman.biking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KalmanFilterTest {
    @Test
    fun gpsMeasurementPullsEstimateTowardGpsSpeed() {
        val filter = KalmanFilter()

        repeat(8) { filter.updateWithGps(8f, accuracyMeters = 4f, hasBearing = true) }

        assertEquals(8f, filter.getSpeed(), 0.7f)
    }

    @Test
    fun accelerationPredictionIncreasesSpeed() {
        val filter = KalmanFilter()

        filter.predict(acceleration = 1.5f, deltaTimeSeconds = 2f, confidence = 1f)

        assertTrue(filter.getSpeed() > 2f)
    }

    @Test
    fun stationaryDecayReducesSpeedTowardZero() {
        val filter = KalmanFilter()
        repeat(5) { filter.updateWithGps(6f, accuracyMeters = 5f, hasBearing = true) }

        repeat(10) { filter.forceStationary(0.5f) }

        assertTrue(filter.getSpeed() < 1f)
    }
}
