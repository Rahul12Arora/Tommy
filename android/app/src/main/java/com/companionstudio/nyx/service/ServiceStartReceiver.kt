package com.companionstudio.nyx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.companionstudio.nyx.PetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Brings the service back after a reboot and after an app update, without the user opening the app (US-06, R-09).
 * Both broadcasts are allowed to start a foreground service from the background (§14.4).
 */
class ServiceStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as PetApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (container.setupSettings.completed.first()) PetService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
