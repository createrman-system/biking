package com.createrman.biking.di

import androidx.room.Room
import com.createrman.biking.SensorDataCollector
import com.createrman.biking.data.local.BikingDatabase
import com.createrman.biking.data.settings.SettingsRepository
import com.createrman.biking.data.tracking.GpxExporter
import com.createrman.biking.data.tracking.TrackingRepository
import com.createrman.biking.domain.usecase.PauseRideUseCase
import com.createrman.biking.domain.usecase.ResumeRideUseCase
import com.createrman.biking.domain.usecase.StartRideUseCase
import com.createrman.biking.domain.usecase.StopRideUseCase
import com.createrman.biking.domain.usecase.UpdateSettingsUseCase
import com.createrman.biking.ui.BikingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(androidContext(), BikingDatabase::class.java, "biking.db")
            .fallbackToDestructiveMigration(false)
            .build()
    }
    single { get<BikingDatabase>().rideDao() }
    single { SensorDataCollector(androidContext()) }
    single { SettingsRepository(androidContext()) }
    single { GpxExporter(androidContext()) }
    single { TrackingRepository(androidContext(), get(), get(), get(), get()) }
    factory { StartRideUseCase(get()) }
    factory { PauseRideUseCase(get()) }
    factory { ResumeRideUseCase(get()) }
    factory { StopRideUseCase(get()) }
    factory { UpdateSettingsUseCase(get()) }
    viewModel { BikingViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
