package com.companionstudio.nyx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.companionstudio.nyx.PetApp
import com.companionstudio.nyx.pet.BreakScreenOverlay
import com.companionstudio.nyx.pet.PetOverlay
import com.companionstudio.nyx.rules.BreakScreenChange
import com.companionstudio.nyx.rules.Reaction
import com.companionstudio.nyx.setup.SetupPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The always-on foreground service (§14.4). It runs the [Engine] while the screen is on, draws the pet (§14.5) and
 * the break screen (§14.7), and posts what the engine decides. Screens never call it; they write to storage (D-44).
 */
class PetService : LifecycleService() {

    private val screenOn = MutableStateFlow(true)
    private val overlayAllowed = MutableStateFlow(false)

    // Not hidden and allowed to draw: the window is on screen, and bubbles can show (§14.6)
    private val petOnScreen = MutableStateFlow(false)

    private lateinit var engine: Engine
    private lateinit var overlay: PetOverlay
    private lateinit var breakScreen: BreakScreenOverlay

    /** `null` while the pet is shown; N-01 says until when it's hidden. */
    private var hiddenUntil: Instant? = null

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
        engine = Engine(
            context = this,
            container = container,
            screenOn = screenOn,
            petOnScreen = petOnScreen,
            onOverlayAllowed = { overlayAllowed.value = it },
            onReaction = ::react,
        )
        overlay = PetOverlay(this, container, request = { engine.requests.trySend(it) })
        breakScreen = BreakScreenOverlay(this, container, onAnswer = { engine.requests.trySend(EngineRequest.BreakScreenAnswer(it)) })
        lifecycleScope.launch {
            // The screen-on flow re-checks the hide time (D-74)
            combine(container.petVisibility.shown(screenOn), overlayAllowed) { shown, allowed -> shown && allowed }
                .collect { petOnScreen.value = it }
        }
        overlay.start(lifecycleScope, petOnScreen)
        breakScreen.start(lifecycleScope, petOnScreen)
        lifecycleScope.launch {
            combine(container.petVisibility.shown(screenOn), container.petSettings.hiddenUntil, ::Pair)
                .collect { (shown, until) ->
                    // A hide time that has passed is cleared, so setting the clock back later can't hide the pet again.
                    // Checked against the clock, not `shown`: right after Hide, `shown` may not have caught up yet
                    if (until != null && !until.isAfter(container.clock.instant())) container.petVisibility.show()
                    val hidden = if (shown) null else until
                    // onStartCommand posts N-01 with the current value; only a change needs a new one
                    if (hidden == hiddenUntil) return@collect
                    hiddenUntil = hidden
                    Notifications.updatePetRunning(this@PetService, hidden)
                }
        }

        lifecycleScope.launch {
            // A session started or ended from an app screen: the engine reacts at once instead of at its next tick (§14.8)
            container.focusSessionDao.observeActive().map { it?.id }.distinctUntilChanged().drop(1)
                .collect { engine.requests.trySend(EngineRequest.Tick) }
        }

        // Blocking network calls, off the engine coroutine: a slow server never delays a tick (§14.12)
        container.configRefresher?.let { refresher ->
            lifecycleScope.launch(Dispatchers.IO) { refresher.keepFresh(screenOn) }
        }

        // Off the main thread: reading the event log and package info are blocking calls, and the app's screens
        // share this process. Still one coroutine, so engine state has one owner (§3)
        lifecycleScope.launch(Dispatchers.Default) {
            // A new usage source (a debug scenario, D-67) gets a fresh tracker
            container.usageSource.collectLatest { source -> engine.run(source) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0 // before Android 14 the manifest's type is used
        }
        ServiceCompat.startForeground(this, Notifications.PET_RUNNING_ID, Notifications.petRunning(this, hiddenUntil), type)
        // Checked here too, not only at ticks: back from granting it, the app opening starts us and the pet shows at once
        overlayAllowed.value = (application as PetApp).container.permissionChecks.isGranted(SetupPermission.OVERLAY)
        // After the process is killed, Android recreates the service (R-09)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay.onConfigurationChanged()
    }

    override fun onDestroy() {
        breakScreen.destroy()
        overlay.destroy()
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    /** Does what a tick decided (§14.7). Called on the engine coroutine; windows are changed on the main thread. */
    private fun react(reaction: Reaction) {
        reaction.nagNotifications.forEach { Notifications.showNag(this, it) }
        reaction.breakNotification?.let { Notifications.showBreakReminder(this, it) }
        if (reaction.breakTaken) Notifications.cancelBreakReminder(this)
        lifecycleScope.launch {
            overlay.update(reaction.glowing, reaction.status)
            reaction.bubble?.let(overlay::showBubble)
            when (val change = reaction.breakScreen) {
                is BreakScreenChange.Show -> {
                    // The ring and any bubble make way for it
                    overlay.makeWayForBreakScreen()
                    breakScreen.show(change)
                }
                BreakScreenChange.Remove -> breakScreen.remove()
                null -> Unit
            }
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
