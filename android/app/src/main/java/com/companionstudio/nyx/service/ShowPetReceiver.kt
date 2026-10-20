package com.companionstudio.nyx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.companionstudio.nyx.PetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** N-01's "Show pet" action while the pet is hidden (US-29). It writes storage; the service follows it (D-44). */
class ShowPetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PetApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.petVisibility.show()
            } finally {
                pending.finish()
            }
        }
    }
}
