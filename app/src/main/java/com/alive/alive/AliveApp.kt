package com.alive.alive

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.alive.alive.data.AliveDatabase
import com.alive.alive.data.SettingsRepository

class AliveApp : Application(), Configuration.Provider {

    lateinit var settingsRepo: SettingsRepository
        private set

    val database: AliveDatabase by lazy { AliveDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepo = SettingsRepository(this)
        WorkManager.initialize(this, workManagerConfiguration)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    companion object {
        @Volatile
        lateinit var instance: AliveApp
            private set
    }
}
