package com.createrman.biking.domain.usecase

import com.createrman.biking.data.settings.SettingsRepository
import com.createrman.biking.data.tracking.TrackingRepository
import com.createrman.biking.domain.model.AppThemeMode
import com.createrman.biking.domain.model.SpeedUnit

class StartRideUseCase(private val repository: TrackingRepository) {
    operator fun invoke(name: String, note: String) = repository.start(name, note)
}

class PauseRideUseCase(private val repository: TrackingRepository) {
    operator fun invoke() = repository.pause()
}

class ResumeRideUseCase(private val repository: TrackingRepository) {
    operator fun invoke() = repository.resume()
}

class StopRideUseCase(private val repository: TrackingRepository) {
    suspend operator fun invoke(name: String, note: String) = repository.stopAndSave(name, note)
}

class UpdateSettingsUseCase(private val repository: SettingsRepository) {
    suspend fun setSpeedUnit(unit: SpeedUnit) = repository.setSpeedUnit(unit)
    suspend fun setThemeMode(mode: AppThemeMode) = repository.setThemeMode(mode)
    suspend fun setAutoPauseEnabled(enabled: Boolean) = repository.setAutoPauseEnabled(enabled)
    suspend fun setAutoPauseDelay(seconds: Int) = repository.setAutoPauseDelay(seconds)
}
