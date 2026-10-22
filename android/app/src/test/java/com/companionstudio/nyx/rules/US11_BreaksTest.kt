package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.ObstructiveConfig
import com.companionstudio.nyx.tracking.EndedBreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** SM-03 and the break screen (§14.7): reminders at 45 min, then every 10 min; the obstructive defaults. */
class US11_BreaksTest {

    private val gentle = BreakRules(enabled = true, afterMs = min(45), repeatMs = min(10), mode = BreakMode.GENTLE, screenAllowed = true)
    private val obstructive = gentle.copy(mode = BreakMode.OBSTRUCTIVE)
    private val screenConfig = ObstructiveConfig()

    private fun min(minutes: Long) = minutes * 60_000

    private fun ended(beforeMinutes: Long) = EndedBreak(Instant.parse("2026-10-21T11:00:00Z"), min(beforeMinutes))

    private fun step(state: BreakState, continuousMinutes: Long, rules: BreakRules = gentle, breaks: List<EndedBreak> = emptyList()) =
        advanceBreaks(state, min(continuousMinutes), breaks, rules)

    @Test
    fun US11_theFirstReminder_comesAtTheLimit_thenEveryRepeatMinutes() {
        val before = step(BreakState(), 44)
        val first = step(before.state, 45)
        val between = step(first.state, 54)
        val second = step(between.state, 55)

        assertEquals(BreakAction.NONE, before.action)
        assertEquals(BreakAction.REMIND, first.action)
        assertEquals(BreakAction.NONE, between.action)
        assertEquals(BreakAction.REMIND, second.action)
    }

    @Test
    fun US11_aBreakAfterADueReminder_counts_andStartsANewSitting() {
        val reminded = step(BreakState(), 50).state

        val back = step(reminded, 0, breaks = listOf(ended(beforeMinutes = 50)))

        assertTrue(back.taken)
        assertEquals(BreakState(), back.state)
        assertEquals(BreakAction.NONE, back.action)
    }

    @Test
    fun A26_aBreakBeforeTheLimit_doesntCount_butStillStartsANewSitting() {
        val back = step(BreakState(nextDueMs = min(60)), 0, breaks = listOf(ended(beforeMinutes = 30)))

        assertFalse(back.taken)
        assertEquals(BreakState(), back.state)
    }

    @Test
    fun breaksSwitchedOff_noReminders_andNoBreakCounts() {
        val off = gentle.copy(enabled = false)

        assertEquals(BreakAction.NONE, step(BreakState(), 90, off).action)
        assertFalse(step(BreakState(), 0, off, listOf(ended(90))).taken)
    }

    @Test
    fun D88_obstructive_showsTheScreen_insteadOfAReminder_andNothingElseWhileItShows() {
        val shown = step(BreakState(), 45, obstructive)
        val showing = step(shown.state, 70, obstructive)

        assertEquals(BreakAction.SHOW_SCREEN, shown.action)
        assertTrue(shown.state.screenShowing)
        assertEquals(BreakAction.NONE, showing.action)
        assertFalse(showing.removeScreen)
    }

    @Test
    fun D88_fiveMoreMinutes_bringsItBackAfterFiveMinutesOfScreenUse_atMostThreeTimes() {
        var state = step(BreakState(), 45, obstructive).state
        var at = 45L
        repeat(3) { snooze ->
            assertTrue(canSnoozeBreakScreen(state, screenConfig.maxSnoozes))
            state = answerBreakScreen(state, takeBreak = false, min(at), screenConfig)
            assertEquals(snooze + 1, state.screenSnoozes)
            assertEquals(BreakAction.NONE, step(state, at + 4, obstructive).action)
            at += 5
            val back = step(state, at, obstructive)
            assertEquals(BreakAction.SHOW_SCREEN, back.action)
            state = back.state
        }

        assertFalse(canSnoozeBreakScreen(state, screenConfig.maxSnoozes))
        // Past the cap, the answer counts as "Take a break"
        val taken = answerBreakScreen(state, takeBreak = false, min(at), screenConfig)
        assertEquals(3, taken.screenSnoozes)
        assertEquals(min(at) + 60_000, taken.nextDueMs)
    }

    @Test
    fun D98_takeABreak_bringsItBackAfter60sOfScreenUse() {
        val shown = step(BreakState(), 45, obstructive).state

        val answered = answerBreakScreen(shown, takeBreak = true, min(46), screenConfig)

        assertFalse(answered.screenShowing)
        assertEquals(BreakAction.NONE, advanceBreaks(answered, min(46) + 59_000, emptyList(), obstructive).action)
        assertEquals(BreakAction.SHOW_SCREEN, advanceBreaks(answered, min(47), emptyList(), obstructive).action)
    }

    @Test
    fun D88_aBreak_removesTheScreen_andResetsTheSnoozes() {
        val snoozed = answerBreakScreen(step(BreakState(), 45, obstructive).state, takeBreak = false, min(45), screenConfig)
        val shownAgain = step(snoozed, 50, obstructive).state

        val back = step(shownAgain, 0, obstructive, listOf(ended(beforeMinutes = 50)))

        assertTrue(back.removeScreen)
        assertTrue(back.taken)
        assertEquals(BreakState(), back.state)
    }

    @Test
    fun D89_whenTheScreenCantShow_itsAGentleReminderInstead() {
        val blocked = obstructive.copy(screenAllowed = false)

        val step = step(BreakState(), 45, blocked)

        assertEquals(BreakAction.REMIND, step.action)
        assertFalse(step.state.screenShowing)
    }

    @Test
    fun D89_aCallWhileItShows_closesIt_untilTheUsualInterval() {
        val shown = step(BreakState(), 45, obstructive).state

        val call = step(shown, 47, obstructive.copy(screenAllowed = false))

        assertTrue(call.removeScreen)
        assertFalse(call.state.screenShowing)
        assertEquals(min(57), call.state.nextDueMs)
        assertEquals(BreakAction.NONE, call.action)
    }

    @Test
    fun D98_theModeIsObstructive_onlyWhenPicked_andAllowed() {
        val picked = ReactionPrefs(mode = BreakMode.OBSTRUCTIVE)

        assertEquals(BreakMode.OBSTRUCTIVE, effectiveBreakMode(picked, allowed = true))
        assertEquals(BreakMode.GENTLE, effectiveBreakMode(picked, allowed = false))
        assertEquals(BreakMode.GENTLE, effectiveBreakMode(ReactionPrefs(), allowed = true))
    }
}
