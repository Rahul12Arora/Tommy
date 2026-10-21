package com.companionstudio.nyx.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Idle lines every 30 min of counted screen time since the pet last spoke (US-27, D-100). */
class US27_IdleTest {

    private val every = 30 * 60_000L

    private fun min(minutes: Long) = minutes * 60_000

    @Test
    fun theFirstTick_startsCountingFromNow() {
        val first = idleStep(baselineMs = null, totalMs = min(200), every, enabled = true)

        assertFalse(first.offer)
        assertEquals(min(200), first.baselineMs)
    }

    @Test
    fun US27_dueAfter30Minutes_thenCountsAgainFromThere() {
        assertFalse(idleStep(min(10), min(39), every, enabled = true).offer)

        val due = idleStep(min(10), min(40), every, enabled = true)

        assertTrue(due.offer)
        assertEquals(min(40), due.baselineMs)
    }

    @Test
    fun US27_switchedOff_neverOffers() {
        assertFalse(idleStep(0, min(90), every, enabled = false).offer)
    }

    @Test
    fun aNewDay_theTotalDrops_andCountingStartsAgain() {
        val newDay = idleStep(min(300), min(5), every, enabled = true)

        assertFalse(newDay.offer)
        assertEquals(min(5), newDay.baselineMs)
    }
}
