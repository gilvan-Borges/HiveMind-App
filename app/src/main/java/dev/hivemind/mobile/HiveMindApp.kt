package dev.hivemind.mobile

import android.app.Application

class HiveMindApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.boot()
    }
}
