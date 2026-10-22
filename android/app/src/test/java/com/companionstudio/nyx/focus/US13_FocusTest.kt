package com.companionstudio.nyx.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** SM-04 one step at a time (§14.8, D-110, D-111, D-113, D-114). */
class US13_FocusTest {

    private val start = Instant.parse("2026-10-21T10:00:00Z")
    private val rules = FocusRules(abandonAfterMs = 30 * MIN, checkinMs = 10 * MIN)

    private fun input(
        credited: Map<String, Long> = emptyMap(),
        front: String? = YOUTUBE,
        screenOn: Boolean = true,
        inCall: Boolean = false,
        atMinute: Long = 1,
    ) = FocusInput(credited, front, screenOn, inCall, start.plusSeconds(atMinute * 60))

    private fun step(
        session: FocusSession = session(),
        phase: FocusPhase = FocusPhase.RUNNING,
        input: FocusInput = input(),
        checkinIndex: Int = 0,
        rules: FocusRules = this.rules,
    ) = stepFocus(session.copy(startedAtMs = start.toEpochMilli(), lastFocusAtMs = start.toEpochMilli()), phase, checkinIndex, input, rules)

    @Test
    fun D110_focusTime_isOnlyWhatTheAllowedAppsWereCredited() {
        val result = step(input = input(credited = mapOf(YOUTUBE to 5_000, "com.android.chrome" to 2_000), front = YOUTUBE))

        assertEquals(5_000, result.session.focusMs)
        assertEquals(input().now.toEpochMilli(), result.session.lastFocusAtMs)
    }

    @Test
    fun D110_noCreditForAllowedApps_leavesFocusTimeAndItsMoment() {
        val result = step(session(focusMs = 60_000), input = input(credited = emptyMap(), front = null))

        assertEquals(60_000, result.session.focusMs)
        assertEquals(start.toEpochMilli(), result.session.lastFocusAtMs)
        assertEquals(FocusPhase.RUNNING, result.phase)
    }

    @Test
    fun US13_aNotAllowedAppInFront_drifts_countsOneSwitch_andNamesIt() {
        val result = step(input = input(credited = mapOf(INSTAGRAM to 4_000), front = INSTAGRAM))

        assertEquals(FocusPhase.DRIFTING, result.phase)
        assertEquals(1, result.session.switches)
        assertEquals(INSTAGRAM, result.driftedTo)
        assertNull(result.outcome)
    }

    @Test
    fun D111_aPeekBetweenTicks_isASwitch_evenWhenBackInAnAllowedApp() {
        val result = step(input = input(credited = mapOf(INSTAGRAM to 3_000, YOUTUBE to 2_000), front = YOUTUBE))

        assertEquals(FocusPhase.RUNNING, result.phase)
        assertEquals(1, result.session.switches)
        assertEquals(INSTAGRAM, result.driftedTo)
    }

    @Test
    fun D111_stayingInTheDrift_doesntCountAgain() {
        val result = step(session(switches = 1), FocusPhase.DRIFTING, input(credited = mapOf(INSTAGRAM to 5_000), front = INSTAGRAM))

        assertEquals(FocusPhase.DRIFTING, result.phase)
        assertEquals(1, result.session.switches)
        assertNull(result.driftedTo)
    }

    @Test
    fun A02_launcherShadeNyxOrNothingInFront_isNeverASwitch() {
        // Those are never counted apps, so the tracker reports no front app and credits nothing to them
        val running = step(input = input(front = null))
        val drifting = step(session(switches = 1), FocusPhase.DRIFTING, input(front = null))

        assertEquals(FocusPhase.RUNNING, running.phase)
        assertEquals(0, running.session.switches)
        assertEquals("the launcher on the way back doesn't end a drift", FocusPhase.DRIFTING, drifting.phase)
        assertEquals(1, drifting.session.switches)
    }

    @Test
    fun A02_inACall_theDialerIsntASwitch() {
        val result = step(input = input(credited = mapOf(DIALER to 5_000), front = DIALER, inCall = true))

        assertEquals(FocusPhase.RUNNING, result.phase)
        assertEquals(0, result.session.switches)
        assertNull(result.driftedTo)
    }

    @Test
    fun SM04_driftEnds_onlyWhenAnAllowedAppIsInFront() {
        val result = step(session(switches = 1), FocusPhase.DRIFTING, input(credited = mapOf(YOUTUBE to 2_000), front = YOUTUBE))

        assertEquals(FocusPhase.RUNNING, result.phase)
        assertEquals(1, result.session.switches)
    }

    @Test
    fun SM04_screenOff_pauses_andScreenOn_resumes() {
        val paused = step(input = input(front = null, screenOn = false))
        val resumed = step(phase = FocusPhase.PAUSED, input = input(front = null))

        assertEquals(FocusPhase.PAUSED, paused.phase)
        assertEquals(FocusPhase.RUNNING, resumed.phase)
    }

    @Test
    fun US13_noSwitching_breaksAtTheFirstSwitch() {
        val result = step(session(goal = FocusGoal.NO_SWITCHING), input = input(credited = mapOf(INSTAGRAM to 1_000), front = INSTAGRAM))

        assertEquals(FocusOutcome.BROKEN, result.outcome)
        assertEquals(1, result.session.switches)
        assertNull("the broken line replaces the drift line", result.driftedTo)
    }

    @Test
    fun US14_timedGoals_completeWhenFocusTimeReachesTheMinutes() {
        for (goal in listOf(FocusGoal.FIXED_TIME, FocusGoal.NO_SWITCHING)) {
            val almost = step(session(goal = goal, focusMs = 25 * MIN - 6_000), input = input(credited = mapOf(YOUTUBE to 5_000)))
            val reached = step(session(goal = goal, focusMs = 25 * MIN - 6_000), input = input(credited = mapOf(YOUTUBE to 6_000)))

            assertNull(goal.name, almost.outcome)
            assertEquals(goal.name, FocusOutcome.COMPLETED, reached.outcome)
        }
    }

    @Test
    fun D111_theTimerWins_overASwitchInTheSameTick() {
        val session = session(goal = FocusGoal.NO_SWITCHING, focusMs = 25 * MIN - 1_000)
        val result = step(session, input = input(credited = mapOf(YOUTUBE to 1_000, INSTAGRAM to 1_000), front = INSTAGRAM))

        assertEquals(FocusOutcome.COMPLETED, result.outcome)
    }

    @Test
    fun A01_untilDone_neverCompletesByTime() {
        val result = step(session(goal = FocusGoal.UNTIL_DONE, goalMinutes = null, focusMs = 300 * MIN), input = input(credited = mapOf(YOUTUBE to 5_000)))

        assertNull(result.outcome)
    }

    @Test
    fun D113_doneCounts_onlyForUntilDone_afterTheMinimum() {
        assertFalse(canFinishAsDone(session(goal = FocusGoal.UNTIL_DONE, goalMinutes = null, focusMs = 5 * MIN - 1), minDoneMinutes = 5))
        assertTrue(canFinishAsDone(session(goal = FocusGoal.UNTIL_DONE, goalMinutes = null, focusMs = 5 * MIN), minDoneMinutes = 5))
        assertFalse(canFinishAsDone(session(goal = FocusGoal.FIXED_TIME, focusMs = 20 * MIN), minDoneMinutes = 5))
        assertFalse(canFinishAsDone(session(goal = FocusGoal.NO_SWITCHING, focusMs = 20 * MIN), minDoneMinutes = 5))
    }

    @Test
    fun D114_noFocusTimeForTheAbandonTime_endsEarly() {
        val before = step(input = input(front = null, atMinute = 29))
        val after = step(input = input(front = null, screenOn = false, atMinute = 30))
        val stillFocusing = step(input = input(credited = mapOf(YOUTUBE to 1_000), atMinute = 30))

        assertNull(before.outcome)
        assertEquals(FocusOutcome.ENDED_EARLY, after.outcome)
        assertNull(stillFocusing.outcome)
    }

    @Test
    fun US13_checkIns_comeEveryIntervalOfFocusTime_withTheMinutesSoFar() {
        val first = step(session(focusMs = 10 * MIN - 2_000), input = input(credited = mapOf(YOUTUBE to 5_000)))
        val again = step(first.session, input = input(credited = mapOf(YOUTUBE to 5_000)), checkinIndex = first.checkinIndex)

        assertEquals(10, first.checkinAtMinutes)
        assertEquals(1, first.checkinIndex)
        assertNull(again.checkinAtMinutes)
    }

    @Test
    fun US13_noCheckIns_whenTheSessionDoesntCheckIn() {
        val result = step(session(focusMs = 10 * MIN - 2_000), input = input(credited = mapOf(YOUTUBE to 5_000)), rules = rules.copy(checkinMs = null))

        assertNull(result.checkinAtMinutes)
    }

    @Test
    fun US13_afterARestart_theRebuiltIndex_doesntCheckInAtOnce() {
        val index = checkinIndexAt(34 * MIN, 10 * MIN)
        val result = step(session(focusMs = 34 * MIN), input = input(credited = mapOf(YOUTUBE to 5_000)), checkinIndex = index)

        assertEquals(3, index)
        assertNull(result.checkinAtMinutes)
        assertEquals(0, checkinIndexAt(34 * MIN, null))
    }

    private companion object {
        const val MIN = 60_000L
    }
}
