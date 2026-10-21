package com.companionstudio.nyx.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** The actions ring's geometry (D-87, D-91, D-101, D-102, A-32). */
class US07_RingLayoutTest {

    private val half = 148 // px, with density 1

    @Test
    fun D87_theSlotsFaceTheMiddle_fromStraightAboveThePetToStraightBelow_onBothEdges() {
        for (side in Side.entries) {
            val ring = ringGeometry(side, slotCount = 7, maxWidth = 400f)
            val inward = if (side == Side.LEFT) 1 else -1

            assertTrue(ring.slots.all { (it.x - ring.pet.x) * inward >= -0.01f })
            assertEquals(ring.pet.y - RING_RADIUS_DP, ring.slots.first().y, 0.01f)
            assertEquals(ring.pet.y + RING_RADIUS_DP, ring.slots.last().y, 0.01f)
            assertEquals(ring.pet.x + inward * RING_RADIUS_DP, ring.slots[3].x, 0.01f)
            ring.slots.forEach { assertEquals(RING_RADIUS_DP, hypot(it.x - ring.pet.x, it.y - ring.pet.y), 0.01f) }
        }
    }

    @Test
    fun D101_neighbouringSlots_neverOverlap_with6Or7() {
        for (count in listOf(6, 7)) {
            val slots = ringGeometry(Side.RIGHT, count, maxWidth = 400f).slots
            slots.zipWithNext().forEach { (a, b) -> assertTrue(hypot(a.x - b.x, a.y - b.y) > RING_SLOT_DP) }
        }
    }

    @Test
    fun theCaption_sitsBesideTheRing_towardsTheMiddle_andShrinksOnANarrowScreen() {
        val right = ringGeometry(Side.RIGHT, 7, maxWidth = 400f)
        val left = ringGeometry(Side.LEFT, 7, maxWidth = 400f)
        val narrow = ringGeometry(Side.RIGHT, 7, maxWidth = 300f)

        assertEquals(RING_CAPTION_DP, right.captionWidth, 0.01f)
        assertTrue(right.captionLeft + right.captionWidth <= right.slots[3].x - RING_SLOT_DP / 2)
        assertTrue(left.captionLeft >= left.slots[3].x + RING_SLOT_DP / 2)
        assertTrue(narrow.width <= 300f)
        assertEquals(2 * RING_HALF_HEIGHT_DP, right.height, 0.01f)
    }

    @Test
    fun D102_theBox_isCentredOnThePet_whenItFits() {
        assertEquals(RingBox(top = 400 - half, height = 2 * half, petCenterY = 400), ringBox(petCenterY = 400, areaHeight = 2000, halfHeightPx = half))
    }

    @Test
    fun D91_nearTheTopOrBottom_thePetMovesAlongItsEdge_justEnoughToFit() {
        val top = ringBox(petCenterY = 50, areaHeight = 2000, halfHeightPx = half)
        val bottom = ringBox(petCenterY = 1990, areaHeight = 2000, halfHeightPx = half)

        assertEquals(RingBox(top = 0, height = 2 * half, petCenterY = half), top)
        assertEquals(RingBox(top = 2000 - 2 * half, height = 2 * half, petCenterY = 2000 - half), bottom)
    }

    @Test
    fun anAreaShorterThanTheBox_centresIt() {
        val box = ringBox(petCenterY = 30, areaHeight = 200, halfHeightPx = half)

        assertEquals(100, box.petCenterY)
        assertEquals(100 - half, box.top)
    }
}
