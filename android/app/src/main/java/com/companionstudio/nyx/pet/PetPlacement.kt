package com.companionstudio.nyx.pet

import kotlin.math.roundToInt

enum class Side { LEFT, RIGHT }

/** Where the pet sits: an edge, and how far down the usable height it is, 0 = top, 1 = bottom (D-73). */
data class PetPosition(val side: Side, val yFraction: Float) {
    companion object {
        val DEFAULT = PetPosition(Side.RIGHT, 0.3f)
    }
}

/** The part of the screen the overlay window may use, in px: the screen minus the system bars and cutout. */
data class Area(val width: Int, val height: Int)

/**
 * How the window is anchored so the pet stays at the same pixel whether the panel is open or not (D-71):
 * to the pet's [side], and to the top (`fromBottom = false`) or bottom edge, [offsetY] px away from it.
 * The panel opens on the side away from that edge.
 */
data class Placement(val side: Side, val fromBottom: Boolean, val offsetY: Int)

fun placement(position: PetPosition, area: Area, petPx: Int): Placement {
    val travel = (area.height - petPx).coerceAtLeast(0)
    val top = (position.yFraction * travel).roundToInt()
    return if (position.yFraction <= 0.5f) {
        Placement(position.side, fromBottom = false, offsetY = top)
    } else {
        Placement(position.side, fromBottom = true, offsetY = travel - top)
    }
}

/** The panel's room between the pet and the far edge; the same sum whichever edge the window is anchored to. */
fun panelMaxHeight(placement: Placement, area: Area, petPx: Int): Int =
    (area.height - placement.offsetY - petPx).coerceAtLeast(0)

/** The pet's top-left corner in the area while the panel is closed, the start point of a drag. */
fun petTopLeft(placement: Placement, area: Area, petPx: Int): Pair<Int, Int> {
    val left = if (placement.side == Side.LEFT) 0 else area.width - petPx
    val top = if (placement.fromBottom) area.height - placement.offsetY - petPx else placement.offsetY
    return left to top
}

/** On release: the edge whose half holds the pet's centre, at the height it was dropped (US-06). */
fun snapToEdge(left: Int, top: Int, area: Area, petPx: Int): PetPosition {
    val side = if (left + petPx / 2 < area.width / 2) Side.LEFT else Side.RIGHT
    val travel = area.height - petPx
    val yFraction = if (travel <= 0) 0f else (top.toFloat() / travel).coerceIn(0f, 1f)
    return PetPosition(side, yFraction)
}
