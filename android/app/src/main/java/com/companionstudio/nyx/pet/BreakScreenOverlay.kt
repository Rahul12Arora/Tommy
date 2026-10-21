package com.companionstudio.nyx.pet

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.companionstudio.nyx.AppContainer
import com.companionstudio.nyx.rules.BreakScreenChange
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The break screen's window (O-04, §14.7): full screen over every app, above the pet's window. Not focusable, so it
 * never takes the keyboard or Back; Android keeps the status bar and navigation above it, so the phone is never locked
 * (D-07). [onAnswer] gets `true` for "Take a break". Main thread only.
 */
class BreakScreenOverlay(
    service: Context,
    private val container: AppContainer,
    private val onAnswer: (takeBreak: Boolean) -> Unit,
) {
    // A window context, like the pet's (D-78)
    private val windowContext = service.getSystemService(DisplayManager::class.java)
        .getDisplay(Display.DEFAULT_DISPLAY)
        .let { service.createDisplayContext(it).createWindowContext(LayoutParams.TYPE_APPLICATION_OVERLAY, null) }
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()

    private var face by mutableStateOf("")
    private var shown by mutableStateOf<BreakScreenChange.Show?>(null)

    // A new showing restarts the button delay
    private var showings by mutableIntStateOf(0)
    private var attached = false

    private val params = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT,
        LayoutParams.TYPE_APPLICATION_OVERLAY,
        LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply { title = "Nyx break screen" }

    private val root = ComposeView(windowContext).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent {
            val change = shown ?: return@setContent
            NyxTheme {
                key(showings) {
                    BreakScreen(
                        face = face,
                        continuousMs = change.continuousMs,
                        line = change.line,
                        buttonDelaySeconds = change.buttonDelaySeconds,
                        onTakeBreak = { answer(takeBreak = true) },
                        onFiveMore = if (change.canSnooze) ({ answer(takeBreak = false) }) else null,
                    )
                }
            }
        }
    }

    /** [onScreen]: the pet may draw; when it can't (hidden, permission gone), the screen goes too. */
    fun start(scope: CoroutineScope, onScreen: Flow<Boolean>) {
        scope.launch { container.summaryRepository.observeToday().collect { face = it.petFace } }
        scope.launch { onScreen.collect { if (!it) remove() } }
    }

    fun show(change: BreakScreenChange.Show) {
        shown = change
        showings++
        if (attached) return
        try {
            windowManager.addView(root, params)
            attached = true
        } catch (e: WindowManager.BadTokenException) {
            // The permission went away since it was last checked; the engine falls back at the next tick
            Log.w(TAG, "Break screen not added", e)
        }
    }

    fun remove() {
        shown = null
        if (!attached) return
        windowManager.removeView(root)
        attached = false
    }

    fun destroy() {
        remove()
        owner.destroy()
    }

    private fun answer(takeBreak: Boolean) {
        remove()
        onAnswer(takeBreak)
    }

    private companion object {
        const val TAG = "Nyx"
    }
}
