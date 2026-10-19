package com.companionstudio.nyx

import android.app.Application
import com.companionstudio.nyx.service.Notifications

class PetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}
