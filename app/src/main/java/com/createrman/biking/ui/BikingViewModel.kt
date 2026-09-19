package com.createrman.biking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.createrman.biking.data.settings.SettingsRepository
import com.createrman.biking.data.tracking.TrackingRepository
import com.createrman.biking.domain.model.AppThemeMode
import com.createrman.biking.domain.model.RideDetails
import com.createrman.biking.domain.model.RideSummary
import com.createrman.biking.domain.model.SpeedUnit
import com.createrman.biking.domain.model.TrackingSettings
import com.createrman.biking.domain.model.TrackingUiState
import com.createrman.biking.domain.usecase.PauseRideUseCase
import com.createrman.biking.domain.usecase.ResumeRideUseCase
import com.createrman.biking.domain.usecase.StartRideUseCase
import com.createrman.biking.domain.usecase.StopRideUseCase
import com.createrman.biking.domain.usecase.UpdateSettingsUseCase
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BikingViewModel(
    private val trackingRepository: TrackingRepository,
    settingsRepository: SettingsRepository,
    private val startRide: StartRideUseCase,
    private val pauseRide: PauseRideUseCase,
    private val resumeRide: ResumeRideUseCase,
    private val stopRide: StopRideUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : ViewModel() {
    val trackingState: StateFlow<TrackingUiState> = trackingRepository.uiState

    val settings: StateFlow<TrackingSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TrackingSettings(),
    )

    val rides: StateFlow<List<RideSummary>> = trackingRepository.rides.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun start(name: String, note: String) = startRide(name, note)
    fun pause() = pauseRide()
    fun resume() = resumeRide()

    fun stop(name: String, note: String) {
        viewModelScope.launch { stopRide(name, note) }
    }

    fun updateMetadata(name: String, note: String) = trackingRepository.updateRideMetadata(name, note)

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch { updateSettings.setSpeedUnit(unit) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { updateSettings.setThemeMode(mode) }
    }

    fun setAutoPause(enabled: Boolean) {
        viewModelScope.launch { updateSettings.setAutoPauseEnabled(enabled) }
    }

    suspend fun getRideDetails(rideId: Long): RideDetails? = trackingRepository.getRideDetails(rideId)

    suspend fun exportRide(rideId: Long): File? = trackingRepository.exportRide(rideId)
}
