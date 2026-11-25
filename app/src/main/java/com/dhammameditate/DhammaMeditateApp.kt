package com.dhammameditate

import android.app.Application
import com.dhammameditate.di.AppContainer

class DhammaMeditateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
