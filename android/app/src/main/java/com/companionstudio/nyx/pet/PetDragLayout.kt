package com.companionstudio.nyx.pet

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * The overlay window's root (D-72). Taps go to the Compose content inside; once the finger moves past the touch
 * slop this layout takes the gesture over (the content gets a cancel, so no click) and reports how far the finger
 * has moved, in raw screen pixels: the window moves under the finger, so coordinates local to it would jump.
 * Also reports taps outside the window, which arrive only while the window asks for them.
 */
@SuppressLint("ViewConstructor") // created in code only, never inflated
class PetDragLayout(
    context: Context,
    private val onDragStart: () -> Unit,
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onOutsideTouch: () -> Unit,
) : FrameLayout(context) {

    var dragEnabled = true

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> startDragOnceMoved(event)
        }
        return dragging
    }

    // Only drags arrive here: taps, and their accessibility actions, are the Compose pet's clicks
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            // Nothing inside took the touch; keep the gesture so a drag can still start from here
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                startDragOnceMoved(event)
                if (dragging) onDrag((event.rawX - downX).toInt(), (event.rawY - downY).toInt())
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                onDragEnd()
            }
        }
        return true
    }

    private fun startDragOnceMoved(event: MotionEvent) {
        if (dragging || !dragEnabled || hypot(event.rawX - downX, event.rawY - downY) <= touchSlop) return
        dragging = true
        onDragStart()
    }
}
