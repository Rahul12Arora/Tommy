package com.companionstudio.nyx.service

import android.content.Context
import android.util.Log
import com.companionstudio.nyx.AppContainer
import com.companionstudio.nyx.BuildConfig
import com.companionstudio.nyx.rules.Reaction
import com.companionstudio.nyx.rules.Reactions
import com.companionstudio.nyx.setup.SetupPermission
import com.companionstudio.nyx.tracking.Tracker
import com.companionstudio.nyx.tracking.UsageSource
import com.companionstudio.nyx.tracking.tickDelaySeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** What the pet's buttons ask of the engine (D-103). Each is handled on the engine coroutine, then a tick runs. */
sealed interface EngineRequest {
    /** The ring opened: its numbers must be fresh (D-77). */
    data object Tick : EngineRequest

    data class Snooze(val key: String) : EngineRequest

    data class BreakScreenAnswer(val takeBreak: Boolean) : EngineRequest

    /** Done ([done]) or End on the ring's focus card (D-118). */
    data class EndFocus(val done: Boolean) : EngineRequest
}

/**
 * The engine loop (§3, §14.4, §14.7): ticks while the screen is on, checks the permissions the pet can't do without,
 * credits usage, and hands each tick's [Reaction] to the service. One coroutine owns all engine state, so a tap and a
 * tick never race. One instance per service; [run] starts over with a fresh tracker for each usage source.
 */
class Engine(
    private val context: Context,
    private val container: AppContainer,
    private val screenOn: StateFlow<Boolean>,
    private val petOnScreen: StateFlow<Boolean>,
    private val onOverlayAllowed: (Boolean) -> Unit,
    private val onReaction: (Reaction) -> Unit,
) {
    val requests = Channel<EngineRequest>(Channel.UNLIMITED)

    /** Ticks while the screen is on. When it goes off, one last tick credits up to that moment, then nothing runs. */
    suspend fun run(source: UsageSource) {
        val tracker = container.newTracker(source)
        val reactions = container.newReactions()
        var missingBefore: Set<SetupPermission>? = null
        screenOn.collectLatest { on ->
            do {
                val missing = WATCHED_PERMISSIONS.filterNot(container.permissionChecks::isGranted).toSet()
                when (val alert = permissionAlert(missingBefore, missing)) {
                    is PermissionAlert.Notify -> Notifications.showPermissionLost(context, alert.missing)
                    PermissionAlert.Cancel -> Notifications.cancelPermissionLost(context)
                    PermissionAlert.None -> Unit
                }
                missingBefore = missing
                onOverlayAllowed(SetupPermission.OVERLAY !in missing)
                val delaySeconds = tick(tracker, reactions, hasUsageAccess = SetupPermission.USAGE_ACCESS !in missing)
                // Waits for the next tick, or less if a button asks for something
                if (on) {
                    withTimeoutOrNull(delaySeconds * 1000L / container.clock.speed) { requests.receive() }
                        ?.let { handle(it, reactions) }
                }
            } while (on)
        }
    }

    private suspend fun handle(request: EngineRequest, reactions: Reactions) {
        val config = container.effectiveConfig.first()
        when (request) {
            EngineRequest.Tick -> Unit
            is EngineRequest.Snooze -> if (!reactions.snooze(request.key, config)) Log.i(TAG, "Snooze not possible any more")
            is EngineRequest.BreakScreenAnswer -> reactions.answerBreakScreen(request.takeBreak, config)
            is EngineRequest.EndFocus -> if (!reactions.endFocus(request.done, config)) Log.i(TAG, "No focus session to end")
        }
    }

    /** Returns the seconds until the next tick. A failed tick is logged and the loop carries on (§9). */
    private suspend fun tick(tracker: Tracker, reactions: Reactions, hasUsageAccess: Boolean): Int {
        val config = container.effectiveConfig.first()
        // Without usage access the log reads as empty, so the last app in front would keep collecting time.
        // Nothing is credited and the cursor stays; the catch-up fills the gap once access is back (D-66)
        if (!hasUsageAccess) return config.document.tracking.slowTickSeconds
        return try {
            val snapshot = tracker.tick(config, container.appCategories.observeChoices().first())
            val reaction = reactions.afterTick(
                snapshot,
                config,
                personalityId = container.petSettings.choice.first().personalityId,
                petOnScreen = petOnScreen.value,
                prefs = container.reactionSettings.prefs.first(),
                calls = container.callState,
            )
            onReaction(reaction)
            // Debug builds only: release builds never log which apps the user opens (§9)
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "tick ${snapshot.day}: front=${snapshot.frontPackage} (${snapshot.frontCategory}), " +
                        "stretch=${snapshot.stretchMs / 1000}s, continuous=${snapshot.continuousMs / 1000}s, " +
                        "today=${snapshot.todayMs.mapValues { it.value / 1000 }}s, mood=${reaction.mood}, " +
                        "glow=${reaction.glowing}, nags=${reaction.status.nags.mapValues { it.value.phase }}" +
                        reaction.bubble?.let { ", bubble=\"${it.line}\"" }.orEmpty() +
                        reaction.breakNotification?.let { ", N-02=\"$it\"" }.orEmpty() +
                        reaction.nagNotifications.joinToString("") { ", N-04=\"$it\"" } +
                        reaction.breakScreen?.let { ", breakScreen=$it" }.orEmpty() +
                        reaction.status.focus?.let { ", focus=${it.phase} ${it.focusMs / 1000}s switches=${it.switches}" }.orEmpty(),
                )
            }
            tickDelaySeconds(snapshot, config, glowing = reaction.glowing, focusRunning = reaction.focusRunning)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tick failed", e)
            config.document.tracking.slowTickSeconds
        }
    }

    private companion object {
        const val TAG = "Nyx"
    }
}
