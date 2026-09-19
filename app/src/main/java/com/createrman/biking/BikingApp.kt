package com.createrman.biking

import android.app.Application
import com.createrman.biking.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BikingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BikingApp)
            modules(appModule)
        }
    }
}
