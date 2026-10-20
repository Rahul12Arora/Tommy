package com.companionstudio.nyx.pet

import org.junit.Assert.assertEquals
import org.junit.Test

class US06_PetPlacementTest {

    // A 1080 × 2274 px area (a phone minus its bars) and a 147 px pet, as on the API 37 emulator
    private val area = Area(width = 1080, height = 2274)
    private val pet = 147
    private val travel = area.height - pet

    @Test
    fun US06_release_snapsToTheSideHoldingThePetsCentre() {
        assertEquals(Side.LEFT, snapToEdge(left = 300, top = 500, area = area, petPx = pet).side)
        assertEquals(Side.RIGHT, snapToEdge(left = 600, top = 500, area = area, petPx = pet).side)
        // Centre exactly on the middle line goes right
        assertEquals(Side.RIGHT, snapToEdge(left = 540 - pet / 2, top = 500, area = area, petPx = pet).side)
    }

    @Test
    fun US06_release_keepsTheHeight_asAFractionOfTheTravel() {
        assertEquals(0.5f, snapToEdge(left = 0, top = travel / 2, area = area, petPx = pet).yFraction, 0.001f)
        assertEquals(0f, snapToEdge(left = 0, top = -40, area = area, petPx = pet).yFraction)
        assertEquals(1f, snapToEdge(left = 0, top = area.height, area = area, petPx = pet).yFraction)
    }

    @Test
    fun D71_topHalf_anchorsFromTheTop_soThePanelOpensBelow() {
        val placement = placement(PetPosition(Side.RIGHT, 0.3f), area, pet)

        assertEquals(Placement(Side.RIGHT, fromBottom = false, offsetY = (0.3f * travel).toInt()), placement)
    }

    @Test
    fun D71_bottomHalf_anchorsFromTheBottom_soThePanelOpensAbove() {
        val placement = placement(PetPosition(Side.LEFT, 0.8f), area, pet)

        assertEquals(Side.LEFT, placement.side)
        assertEquals(true, placement.fromBottom)
        assertEquals(travel - Math.round(0.8f * travel), placement.offsetY)
    }

    @Test
    fun D71_thePetSitsAtTheSamePixel_whicheverEdgeTheWindowIsAnchoredTo() {
        for (fraction in listOf(0f, 0.3f, 0.5f, 0.51f, 0.8f, 1f)) {
            val position = PetPosition(Side.RIGHT, fraction)
            val (left, top) = petTopLeft(placement(position, area, pet), area, pet)

            assertEquals("fraction $fraction", area.width - pet, left)
            assertEquals("fraction $fraction", Math.round(fraction * travel), top)
            // Dropping it where it is gives the same position back
            assertEquals(fraction, snapToEdge(left, top, area, pet).yFraction, 0.001f)
        }
    }

    @Test
    fun D73_rotation_keepsTheEdgeAndRelativeHeight() {
        val position = PetPosition(Side.LEFT, 0.75f)
        val landscape = Area(width = 2400, height = 954)

        val (left, top) = petTopLeft(placement(position, landscape, pet), landscape, pet)

        assertEquals(0, left)
        assertEquals(Math.round(0.75f * (landscape.height - pet)), top)
    }

    @Test
    fun D71_panelRoom_isTheSpaceBetweenThePetAndTheFarEdge() {
        val top = placement(PetPosition(Side.RIGHT, 0f), area, pet)
        val bottom = placement(PetPosition(Side.RIGHT, 1f), area, pet)
        val middle = placement(PetPosition(Side.RIGHT, 0.5f), area, pet)

        assertEquals(area.height - pet, panelMaxHeight(top, area, pet))
        assertEquals(area.height - pet, panelMaxHeight(bottom, area, pet))
        assertEquals(area.height - Math.round(0.5f * travel) - pet, panelMaxHeight(middle, area, pet))
    }

    @Test
    fun anAreaSmallerThanThePet_putsItAtTheTop_withNoPanelRoom() {
        val tiny = Area(width = 100, height = 100)

        val placement = placement(PetPosition(Side.RIGHT, 0.9f), tiny, pet)

        assertEquals(0, placement.offsetY)
        assertEquals(0, panelMaxHeight(placement, tiny, pet))
        assertEquals(0f, snapToEdge(left = 0, top = 50, area = tiny, petPx = pet).yFraction)
    }
}
