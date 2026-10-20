package com.companionstudio.nyx

import android.app.Application
import android.util.Log
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.companionstudio.nyx.service.Notifications

class PetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        initEmoji()
    }

    /**
     * Google Play services' emoji font for every emoji, so the pet looks the same on every brand (F-15, D-76).
     * Loads on its own background thread right away, also when only the service runs (after a reboot).
     */
    private fun initEmoji() {
        val config = DefaultEmojiCompatConfig.create(this)
        if (config == null) {
            Log.w(TAG, "No emoji font provider (no Play services?): using the phone's own emoji")
            return
        }
        config.setReplaceAll(true)
        config.registerInitCallback(object : EmojiCompat.InitCallback() {
            override fun onInitialized() {
                Log.d(TAG, "Emoji font loaded")
            }

            override fun onFailed(throwable: Throwable?) {
                Log.w(TAG, "Emoji font not loaded: using the phone's own emoji", throwable)
            }
        })
        EmojiCompat.init(config)
    }

    private companion object {
        const val TAG = "Nyx"
    }
}
