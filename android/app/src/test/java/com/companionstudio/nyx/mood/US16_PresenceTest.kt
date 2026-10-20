package com.companionstudio.nyx.mood

import com.companionstudio.nyx.rules.PetEventType.BREAK_TAKEN
import com.companionstudio.nyx.rules.PetEventType.DOOMSCROLL_10MIN
import com.companionstudio.nyx.rules.PetEventType.PET_RETURNED
import com.companionstudio.nyx.rules.PetEventType.RAN_AWAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class US16_PresenceTest {

    private val doomscrolling = List(24) { DOOMSCROLL_10MIN }

    @Test
    fun US16_moodReaches0_ranAwayOnce() {
        assertEquals(RAN_AWAY, presenceChange(doomscrolling, mood = 0))
        assertNull(presenceChange(doomscrolling + RAN_AWAY, mood = 0))
        assertNull(presenceChange(doomscrolling + RAN_AWAY + DOOMSCROLL_10MIN, mood = 0))
    }

    @Test
    fun US16_moodAbove0_afterRunningAway_returnsOnce() {
        val away = doomscrolling + RAN_AWAY + BREAK_TAKEN

        assertEquals(PET_RETURNED, presenceChange(away, mood = 5))
        assertNull(presenceChange(away + PET_RETURNED, mood = 5))
    }

    @Test
    fun US16_canRunAwayAndReturnAgain_theSameDay() {
        val returned = doomscrolling + RAN_AWAY + BREAK_TAKEN + PET_RETURNED

        assertEquals(RAN_AWAY, presenceChange(returned + DOOMSCROLL_10MIN + DOOMSCROLL_10MIN, mood = 0))
        assertEquals(PET_RETURNED, presenceChange(returned + DOOMSCROLL_10MIN + RAN_AWAY + BREAK_TAKEN, mood = 5))
    }

    @Test
    fun aFreshDay_aboveZero_nothingToSay() {
        assertNull(presenceChange(emptyList(), mood = 70))
        assertNull(presenceChange(listOf(DOOMSCROLL_10MIN), mood = 1))
    }
}
