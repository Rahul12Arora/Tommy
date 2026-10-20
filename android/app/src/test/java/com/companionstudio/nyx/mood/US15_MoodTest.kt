package com.companionstudio.nyx.mood

import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.MoodWeights
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.rules.PetEventType
import com.companionstudio.nyx.rules.PetEventType.BREAK_TAKEN
import com.companionstudio.nyx.rules.PetEventType.DOOMSCROLL_10MIN
import com.companionstudio.nyx.rules.PetEventType.FOCUS_COMPLETED
import com.companionstudio.nyx.rules.PetEventType.GOAL_MET
import com.companionstudio.nyx.rules.PetEventType.LIMIT_EXCEEDED
import com.companionstudio.nyx.rules.PetEventType.PET_RETURNED
import com.companionstudio.nyx.rules.PetEventType.RAN_AWAY
import com.companionstudio.nyx.rules.PetEventType.SNOOZED
import org.junit.Assert.assertEquals
import org.junit.Test

class US15_MoodTest {

    // Start 70; +15 focus, +10 goal, +5 break, −3 doomscroll, −10 limit, −5 snooze (A-24)
    private val mood = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config.mood

    @Test
    fun US15_noEvents_isTheStartValue() {
        assertEquals(70, moodOf(emptyList(), mood))
    }

    @Test
    fun US15_everyWeight_movesMoodByItsConfiguredAmount() {
        val expected = mapOf(
            FOCUS_COMPLETED to 85,
            GOAL_MET to 80,
            BREAK_TAKEN to 75,
            DOOMSCROLL_10MIN to 67,
            LIMIT_EXCEEDED to 60,
            SNOOZED to 65,
            RAN_AWAY to 70,
            PET_RETURNED to 70,
        )

        assertEquals(PetEventType.entries.toSet(), expected.keys) // a new event type must say what it weighs
        expected.forEach { (event, value) -> assertEquals(event.name, value, moodOf(listOf(event), mood)) }
    }

    @Test
    fun US15_weightsComeFromTheConfigInForce() {
        val tuned = mood.copy(start = 50, weights = MoodWeights(1, 2, 3, -4, -6, -7))

        assertEquals(
            50 + 1 + 2 + 3 - 4 - 6 - 7,
            moodOf(listOf(FOCUS_COMPLETED, GOAL_MET, BREAK_TAKEN, DOOMSCROLL_10MIN, LIMIT_EXCEEDED, SNOOZED), tuned),
        )
    }

    @Test
    fun D79_clampedAt100_afterEveryEvent() {
        // 70 + 15 + 15 stops at 100, with nothing banked above it; then −10 → 90
        assertEquals(90, moodOf(listOf(FOCUS_COMPLETED, FOCUS_COMPLETED, LIMIT_EXCEEDED), mood))
    }

    @Test
    fun D79_clampedAt0_afterEveryEvent_soARiseStartsFrom0() {
        val sunk = List(30) { DOOMSCROLL_10MIN } // 70 − 90 would be −20 without clamping

        assertEquals(0, moodOf(sunk, mood))
        assertEquals(5, moodOf(sunk + BREAK_TAKEN, mood))
    }

    @Test
    fun US15_fourHoursOfDoomscrolling_walksDownToZero_twoHoursOnlyToGrumpy() {
        assertEquals(34, moodOf(List(12) { DOOMSCROLL_10MIN }, mood))
        assertEquals(1, moodOf(List(23) { DOOMSCROLL_10MIN }, mood))
        assertEquals(0, moodOf(List(24) { DOOMSCROLL_10MIN }, mood))
    }

    @Test
    fun US15_everyValue_mapsToItsBand() {
        val expected = mapOf(
            100 to "great", 85 to "great", 84 to "happy", 70 to "happy", 69 to "ok", 50 to "ok", 49 to "meh",
            35 to "meh", 34 to "grumpy", 20 to "grumpy", 19 to "sad", 10 to "sad", 9 to "sick", 1 to "sick",
            0 to "ran_away",
        )

        expected.forEach { (value, band) -> assertEquals("mood $value", band, mood.bandAt(value).id) }
        MOOD_RANGE.forEach { mood.bandAt(it) } // every value has a band
    }
}
