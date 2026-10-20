package com.companionstudio.nyx.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.companionstudio.nyx.AppContainer
import com.companionstudio.nyx.MainActivity
import com.companionstudio.nyx.home.TodaySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The pet on screen (O-01) and its summary panel (O-03), in one overlay window owned by the pet service (§14.5).
 * The window is the pet's size while the panel is closed, so taps beside it reach the app below (US-06).
 * Main thread only.
 */
class PetOverlay(
    private val service: Context,
    private val container: AppContainer,
    private val requestTick: () -> Unit,
) {
    // A window context, not the service's: Android expects UI to come from a visual context (D-78)
    private val windowContext = service.getSystemService(DisplayManager::class.java)
        .getDisplay(Display.DEFAULT_DISPLAY)
        .let { service.createDisplayContext(it).createWindowContext(LayoutParams.TYPE_APPLICATION_OVERLAY, null) }
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private val density = windowContext.resources.displayMetrics.density
    private val petPx = (PET_SIZE.value * density).roundToInt()
    private val owner = OverlayOwner()

    // Read by the Compose content
    private var summary by mutableStateOf<TodaySummary?>(null)
    private var panelOpen by mutableStateOf(false)
    private var panelLayout by mutableStateOf(PanelLayout(Side.RIGHT, below = true, maxHeight = 0.dp))
    private var hideMinutes by mutableIntStateOf(0)
    private var focusEnabled by mutableStateOf(false)

    private lateinit var scope: CoroutineScope
    private var position = PetPosition.DEFAULT
    private var attached = false

    /** Where the pet's top-left corner was when the current drag started; `null` when not dragging. */
    private var dragStart: Pair<Int, Int>? = null

    private val params = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
        LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Never takes the keyboard or Back from the app below (H-05)
        LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Coordinates are measured inside the same area area() describes
        fitInsetsTypes = INSET_TYPES
        title = "Nyx pet"
    }

    private val root = PetDragLayout(
        windowContext,
        onDragStart = ::startDrag,
        onDrag = ::drag,
        onDragEnd = ::endDrag,
        onOutsideTouch = ::closePanel,
    ).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        addView(
            ComposeView(windowContext).apply {
                setContent {
                    PetWindow(
                        summary = summary,
                        panelOpen = panelOpen,
                        layout = panelLayout,
                        onPetClick = ::togglePanel,
                        actions = PanelActions(
                            hideMinutes = hideMinutes,
                            onFocus = if (focusEnabled) ::openFocus else null,
                            onOpenApp = ::openApp,
                            onHide = ::hide,
                        ),
                    )
                }
            },
        )
    }

    /** [screenOn] re-checks the hide time (D-74); [overlayAllowed] follows the "display over other apps" permission. */
    fun start(scope: CoroutineScope, screenOn: Flow<Boolean>, overlayAllowed: Flow<Boolean>) {
        this.scope = scope
        scope.launch { container.summaryRepository.observeToday().collect { summary = it } }
        scope.launch {
            container.effectiveConfig.collect {
                hideMinutes = it.document.hide.minutes
                focusEnabled = it.document.features.focus
            }
        }
        scope.launch {
            container.petSettings.position.collect {
                position = it
                if (dragStart == null) place()
            }
        }
        scope.launch {
            combine(container.petVisibility.shown(screenOn), overlayAllowed) { shown, allowed -> shown && allowed }
                .distinctUntilChanged()
                .collect { onScreen -> if (onScreen) attach() else detach() }
        }
    }

    /** The screen rotated or changed size: same edge, same relative height. */
    fun onConfigurationChanged() {
        if (dragStart == null) place()
    }

    fun destroy() {
        detach()
        owner.destroy()
    }

    private fun attach() {
        if (attached) return
        place()
        try {
            windowManager.addView(root, params)
            attached = true
        } catch (e: WindowManager.BadTokenException) {
            // The permission went away since it was last checked; the next change of it tries again
            Log.w(TAG, "Pet window not added", e)
        }
    }

    private fun detach() {
        if (!attached) return
        changePanel(false)
        dragStart = null
        windowManager.removeView(root)
        attached = false
    }

    /** Anchors the window so the pet stays put whether the panel is open or not (D-71). */
    @SuppressLint("RtlHardcoded") // the pet sits on a physical edge of the screen, whatever the text direction
    private fun place() {
        val area = area()
        val placement = placement(position, area, petPx)
        val horizontal = if (placement.side == Side.LEFT) Gravity.LEFT else Gravity.RIGHT
        val vertical = if (placement.fromBottom) Gravity.BOTTOM else Gravity.TOP
        params.gravity = horizontal or vertical
        params.x = 0
        params.y = placement.offsetY
        val roomPx = panelMaxHeight(placement, area, petPx) - (PANEL_GAP.value * density).roundToInt()
        panelLayout = PanelLayout(placement.side, below = !placement.fromBottom, maxHeight = (roomPx / density).coerceAtLeast(0f).dp)
        if (attached) windowManager.updateViewLayout(root, params)
    }

    /** The screen minus the system bars and cutout, in px: the space the window's coordinates are measured in. */
    private fun area(): Area {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(INSET_TYPES)
        return Area(
            width = metrics.bounds.width() - insets.left - insets.right,
            height = metrics.bounds.height() - insets.top - insets.bottom,
        )
    }

    @SuppressLint("RtlHardcoded") // see place()
    private fun startDrag() {
        val area = area()
        dragStart = petTopLeft(placement(position, area, petPx), area, petPx)
        params.gravity = Gravity.TOP or Gravity.LEFT
    }

    private fun drag(dx: Int, dy: Int) {
        val (left, top) = dragStart ?: return
        val area = area()
        params.x = (left + dx).coerceIn(0, (area.width - petPx).coerceAtLeast(0))
        params.y = (top + dy).coerceIn(0, (area.height - petPx).coerceAtLeast(0))
        if (attached) windowManager.updateViewLayout(root, params)
    }

    private fun endDrag() {
        if (dragStart == null) return
        dragStart = null
        val dropped = snapToEdge(params.x, params.y, area(), petPx)
        position = dropped
        place()
        scope.launch { container.petSettings.savePosition(dropped) }
    }

    private fun togglePanel() {
        changePanel(!panelOpen)
    }

    private fun closePanel() {
        changePanel(false)
    }

    private fun changePanel(open: Boolean) {
        if (open == panelOpen) return
        panelOpen = open
        // Numbers at most a minute old: tick now; the panel updates when it commits (US-07, D-77)
        if (open) requestTick()
        // Outside taps only matter while the panel is open; watching them always would wake the app on every touch
        params.flags = if (open) {
            params.flags or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            params.flags and LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        }
        root.dragEnabled = !open
        if (attached) windowManager.updateViewLayout(root, params)
    }

    private fun openFocus() = open(MainActivity.Open.FOCUS)

    private fun openApp() = open(MainActivity.Open.HOME)

    // Allowed from the service because the user granted "display over other apps" (§14.5)
    private fun open(target: MainActivity.Open) {
        closePanel()
        service.startActivity(MainActivity.intent(service, target))
    }

    private fun hide() {
        closePanel()
        scope.launch { container.petVisibility.hide(hideMinutes) }
    }

    private companion object {
        const val TAG = "Nyx"
        val INSET_TYPES = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
    }
}
