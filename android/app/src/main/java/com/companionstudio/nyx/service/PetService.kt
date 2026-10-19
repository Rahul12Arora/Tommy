package com.companionstudio.nyx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.companionstudio.nyx.AppContainer
import com.companionstudio.nyx.BuildConfig
import com.companionstudio.nyx.PetApp
import com.companionstudio.nyx.setup.SetupPermission
import com.companionstudio.nyx.tracking.Tracker
import com.companionstudio.nyx.tracking.UsageSource
import com.companionstudio.nyx.tracking.tickDelaySeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The always-on foreground service (§14.4). Its engine coroutine ticks while the screen is on: it checks the
 * permissions the pet can't do without, then credits usage. Screens never call it; they write to storage (D-44).
 */
class PetService : LifecycleService() {

    private val screenOn = MutableStateFlow(true)

    // SCREEN_ON and SCREEN_OFF can only be received by a receiver registered at runtime
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
        }
    }

    override fun onCreate() {
        super.onCreate()
        screenOn.value = getSystemService(PowerManager::class.java).isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val container = (application as PetApp).container
        // Off the main thread: reading the event log and package info are blocking calls, and the app's screens
        // share this process. Still one coroutine, so engine state has one owner (§3)
        lifecycleScope.launch(Dispatchers.Default) {
            // A new usage source (a debug scenario, D-67) gets a fresh tracker
            container.usageSource.collectLatest { source -> runEngine(container, source) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0 // before Android 14 the manifest's type is used
        }
        ServiceCompat.startForeground(this, Notifications.PET_RUNNING_ID, Notifications.petRunning(this), type)
        // After the process is killed, Android recreates the service (R-09)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    /** Ticks while the screen is on. When it goes off, one last tick credits up to that moment, then nothing runs. */
    private suspend fun runEngine(container: AppContainer, source: UsageSource) {
        val tracker = container.newTracker(source)
        var missingBefore: Set<SetupPermission>? = null
        screenOn.collectLatest { on ->
            do {
                val missing = WATCHED_PERMISSIONS.filterNot(container.permissionChecks::isGranted).toSet()
                when (val alert = permissionAlert(missingBefore, missing)) {
                    is PermissionAlert.Notify -> Notifications.showPermissionLost(this, alert.missing)
                    PermissionAlert.Cancel -> Notifications.cancelPermissionLost(this)
                    PermissionAlert.None -> Unit
                }
                missingBefore = missing
                val delaySeconds = tick(container, tracker, hasUsageAccess = SetupPermission.USAGE_ACCESS !in missing)
                if (on) delay(delaySeconds * 1000L / container.clock.speed)
            } while (on)
        }
    }

    /** Returns the seconds until the next tick. A failed tick is logged and the loop carries on (§9). */
    private suspend fun tick(container: AppContainer, tracker: Tracker, hasUsageAccess: Boolean): Int {
        val config = container.effectiveConfig.first()
        // Without usage access the log reads as empty, so the last app in front would keep collecting time.
        // Nothing is credited and the cursor stays; the catch-up fills the gap once access is back (D-66)
        if (!hasUsageAccess) return config.document.tracking.slowTickSeconds
        return try {
            val snapshot = tracker.tick(config, container.appCategories.observeChoices().first())
            // Debug builds only: release builds never log which apps the user opens (§9)
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "tick ${snapshot.day}: front=${snapshot.frontPackage} (${snapshot.frontCategory}), " +
                        "stretch=${snapshot.stretchMs / 1000}s, today=${snapshot.todayMs.mapValues { it.value / 1000 }}s",
                )
            }
            tickDelaySeconds(snapshot, config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tick failed", e)
            config.document.tracking.slowTickSeconds
        }
    }

    companion object {
        private const val TAG = "Nyx"

        /** Starts the service, or does nothing if it's already running. Only call once setup is done. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PetService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+) when started from the background
                Log.e(TAG, "Pet service not started", e)
            }
        }
    }
}
