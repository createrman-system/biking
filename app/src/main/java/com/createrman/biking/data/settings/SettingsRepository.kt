package com.createrman.biking.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.createrman.biking.domain.model.AppThemeMode
import com.createrman.biking.domain.model.SpeedUnit
import com.createrman.biking.domain.model.TrackingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "biking_settings")

class SettingsRepository(private val context: Context) {
    private val speedUnitKey = stringPreferencesKey("speed_unit")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val autoPauseEnabledKey = booleanPreferencesKey("auto_pause_enabled")
    private val autoPauseDelayKey = intPreferencesKey("auto_pause_delay_seconds")

    val settings: Flow<TrackingSettings> = context.settingsDataStore.data.map { prefs ->
        TrackingSettings(
            speedUnit = prefs[speedUnitKey]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() } ?: SpeedUnit.Kmh,
            themeMode = prefs[themeModeKey]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() } ?: AppThemeMode.System,
            autoPauseEnabled = prefs[autoPauseEnabledKey] ?: true,
            autoPauseDelaySeconds = prefs[autoPauseDelayKey] ?: 8,
        )
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        context.settingsDataStore.edit { it[speedUnitKey] = unit.name }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setAutoPauseDelay(seconds: Int) {
        context.settingsDataStore.edit { it[autoPauseDelayKey] = seconds.coerceIn(3, 60) }
    }

    suspend fun setAutoPauseEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[autoPauseEnabledKey] = enabled }
    }
}
