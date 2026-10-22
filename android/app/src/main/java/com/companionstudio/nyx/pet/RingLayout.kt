package com.companionstudio.nyx.pet

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Distance from the pet's centre to each slot's centre, in dp (D-101). */
const val RING_RADIUS_DP = 120f

/** A slot's circle, in dp: above the 48 dp touch target (§9). */
const val RING_SLOT_DP = 56f

/** Room for the caption beside the middle slot, in dp, when the screen is wide enough. */
const val RING_CAPTION_DP = 180f

private const val PET_DP = 56f
private const val GAP_DP = 8f

/** Half the ring's height: from the pet's centre to the outer edge of the top or bottom slot. */
const val RING_HALF_HEIGHT_DP = RING_RADIUS_DP + RING_SLOT_DP / 2

/** Where the window goes while the ring is open, in px of the usable area (D-102). */
data class RingBox(val top: Int, val height: Int, val petCenterY: Int)

/**
 * The ring's window around a pet whose centre is at [petCenterY]: tall enough for the half ring, with the pet's centre
 * moved along its edge just enough that the box fits (D-91). When the ring closes the pet goes back to [petCenterY]
 * (A-32). An area shorter than the box gets the box centred; its end slots may be cut off.
 */
fun ringBox(petCenterY: Int, areaHeight: Int, halfHeightPx: Int): RingBox {
    if (areaHeight < 2 * halfHeightPx) return RingBox(top = areaHeight / 2 - halfHeightPx, height = 2 * halfHeightPx, petCenterY = areaHeight / 2)
    val center = petCenterY.coerceIn(halfHeightPx, areaHeight - halfHeightPx)
    return RingBox(top = center - halfHeightPx, height = 2 * halfHeightPx, petCenterY = center)
}

/**
 * The ring box's width in dp for a usable width of [maxWidth] dp: pet + ring + as much caption as fits. The window is
 * given this width explicitly: a wrap-content window is first measured at the dialog width (~320 dp), and the ring
 * would be squeezed into it and run off the screen (found on the Pixel 7a).
 */
fun ringBoxWidth(maxWidth: Float): Float {
    val ringWidth = PET_DP / 2 + RING_RADIUS_DP + RING_SLOT_DP / 2
    return ringWidth + GAP_DP + (maxWidth - ringWidth - GAP_DP).coerceIn(0f, RING_CAPTION_DP)
}

/** A point in the ring's box, in dp from its top-left corner. */
data class DpPoint(val x: Float, val y: Float)

/**
 * Everything inside the ring's box, in dp. [slots] are centres, top to bottom. The caption sits beside the middle slot,
 * towards the middle of the screen; [captionLeft] is its left edge.
 */
data class RingGeometry(
    val width: Float,
    val height: Float,
    val pet: DpPoint,
    val slots: List<DpPoint>,
    val captionLeft: Float,
    val captionWidth: Float,
)

/**
 * [slotCount] slots spread evenly over the half circle that faces the middle of the screen, from straight above the
 * pet to straight below it (D-87, D-101). [maxWidth]: the usable width, so the caption never runs off screen.
 */
fun ringGeometry(side: Side, slotCount: Int, maxWidth: Float): RingGeometry {
    require(slotCount >= 2) { "a ring needs at least 2 slots, had $slotCount" }
    val ringWidth = PET_DP / 2 + RING_RADIUS_DP + RING_SLOT_DP / 2
    val width = ringBoxWidth(maxWidth)
    val captionWidth = width - ringWidth - GAP_DP
    val petX = if (side == Side.LEFT) PET_DP / 2 else width - PET_DP / 2
    // Towards the middle: right for a pet on the left edge, left for one on the right
    val inward = if (side == Side.LEFT) 1 else -1
    val slots = List(slotCount) { i ->
        val angle = -PI / 2 + PI * i / (slotCount - 1)
        DpPoint(petX + inward * RING_RADIUS_DP * cos(angle).toFloat(), RING_HALF_HEIGHT_DP + RING_RADIUS_DP * sin(angle).toFloat())
    }
    val captionLeft = if (side == Side.LEFT) ringWidth + GAP_DP else 0f
    return RingGeometry(width, 2 * RING_HALF_HEIGHT_DP, DpPoint(petX, RING_HALF_HEIGHT_DP), slots, captionLeft, captionWidth)
}
