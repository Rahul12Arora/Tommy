package com.companionstudio.nyx.focus

import com.companionstudio.nyx.config.BubbleTrigger
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.Placeholder
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.rules.CallState
import com.companionstudio.nyx.rules.FakePetEventDao
import com.companionstudio.nyx.rules.PetEventType
import com.companionstudio.nyx.rules.TestClock
import com.companionstudio.nyx.rules.TestLabels
import com.companionstudio.nyx.tracking.TrackerSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** [FocusSessions] with a fake DAO: starts and ends from anywhere, lines per pet mode, progress (§14.8). */
class US12_FocusSessionsTest {

    private val focusConfig = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config.focus
    private val clock = TestClock(Instant.parse("2026-10-21T10:00:00Z"))
    private val events = FakePetEventDao()
    private val dao = FakeFocusSessionDao(events)
    private val noCall = CallState { false }

    private fun snapshot(credited: Map<String, Long> = emptyMap(), front: String? = YOUTUBE, screenOn: Boolean = true) = TrackerSnapshot(
        day = LocalDate.parse("2026-10-21"),
        frontPackage = front,
        frontCategory = null,
        stretchMs = 0,
        todayMs = emptyMap(),
        creditedMs = credited,
        screenOn = screenOn,
    )

    private suspend fun startNow(session: FocusSession = session()) =
        dao.start(session.copy(startedAtMs = clock.now.toEpochMilli(), lastFocusAtMs = clock.now.toEpochMilli()))

    private fun later(seconds: Long) {
        clock.now = clock.now.plusSeconds(seconds)
    }

    private fun FocusTick.triggers() = offers.map { it.trigger }

    @Test
    fun US12_aNewSession_getsTheStartLine_onTheTickThatSeesIt_whichCountsNoTime() = runTest {
        val sessions = FocusSessions(dao, clock)
        later(1)
        startNow()

        val first = sessions.step(snapshot(credited = mapOf(YOUTUBE to 5_000)), focusConfig, noCall, TestLabels)

        assertTrue(first.held)
        assertEquals(listOf(BubbleTrigger.FOCUS_STARTED), first.triggers())
        assertEquals(0L, dao.active()?.focusMs)
        assertNotNull(first.status)
    }

    @Test
    fun D109_aSessionFromBeforeARestart_isPickedUpSilently() = runTest {
        startNow(session(focusMs = 60_000))
        later(60)
        val sessions = FocusSessions(dao, clock)

        val first = sessions.step(snapshot(), focusConfig, noCall, TestLabels)

        assertTrue(first.held)
        assertTrue(first.offers.isEmpty())
        assertEquals(60_000L, first.status?.focusMs)
    }

    @Test
    fun D110_progressIsSaved_onlyWhenSomethingChanged() = runTest {
        val sessions = FocusSessions(dao, clock)
        startNow()
        sessions.step(snapshot(), focusConfig, noCall, TestLabels)

        later(5)
        sessions.step(snapshot(credited = mapOf(YOUTUBE to 5_000)), focusConfig, noCall, TestLabels)
        later(5)
        sessions.step(snapshot(credited = emptyMap(), front = null), focusConfig, noCall, TestLabels)

        assertEquals(5_000L, dao.active()?.focusMs)
        assertEquals(1, dao.progressSaves.size)
    }

    @Test
    fun US14_reachingTheTimer_completes_logsFocusCompletedOnce_andSaysSo() = runTest {
        val sessions = FocusSessions(dao, clock)
        startNow(session(focusMs = 25 * 60_000L - 1_000))
        sessions.step(snapshot(), focusConfig, noCall, TestLabels)

        later(5)
        val done = sessions.step(snapshot(credited = mapOf(YOUTUBE to 5_000)), focusConfig, noCall, TestLabels)
        later(5)
        val after = sessions.step(snapshot(credited = mapOf(YOUTUBE to 5_000)), focusConfig, noCall, TestLabels)

        assertTrue(done.held)
        assertTrue(done.endedNow)
        assertEquals(listOf(BubbleTrigger.FOCUS_COMPLETED), done.triggers())
        assertNull(done.status)
        assertEquals(FocusOutcome.COMPLETED, dao.latest()?.outcome)
        assertEquals(listOf(PetEventType.FOCUS_COMPLETED), events.events.map { it.type })
        assertEquals("1", events.events.single().subject)
        assertFalse(after.held)
        assertFalse(after.endedNow)
        assertTrue(after.offers.isEmpty())
    }

    @Test
    fun D109_anEndFromHome_isNoticedAtTheNextTick_withItsLine() = runTest {
        val sessions = FocusSessions(dao, clock)
        startNow(session(goal = FocusGoal.UNTIL_DONE, goalMinutes = null, focusMs = 6 * 60_000L))
        sessions.step(snapshot(), focusConfig, noCall, TestLabels)

        // What FocusRepository.finish does for Home's Done
        assertTrue(dao.finish(1, FocusOutcome.COMPLETED, clock.now.toEpochMilli(), "2026-10-21"))
        assertFalse("a second finish is a no-op", dao.finish(1, FocusOutcome.ENDED_EARLY, clock.now.toEpochMilli(), "2026-10-21"))
        val next = sessions.step(snapshot(), focusConfig, noCall, TestLabels)

        assertTrue(next.held)
        assertTrue(next.endedNow)
        assertEquals(listOf(BubbleTrigger.FOCUS_COMPLETED), next.triggers())
        assertEquals(1, events.events.count { it.type == PetEventType.FOCUS_COMPLETED })
    }

    @Test
    fun US14_endedEarly_saysNothing_andLogsNothing() = runTest {
        val sessions = FocusSessions(dao, clock)
        startNow()
        sessions.step(snapshot(), focusConfig, noCall, TestLabels)

        assertTrue(sessions.end(done = false, focusConfig, LocalDate.parse("2026-10-21")))
        val next = sessions.step(snapshot(), focusConfig, noCall, TestLabels)

        assertTrue(next.endedNow)
        assertTrue(next.offers.isEmpty())
        assertTrue(events.events.isEmpty())
        assertEquals(FocusOutcome.ENDED_EARLY, dao.latest()?.outcome)
    }

    @Test
    fun D113_doneFromTheRing_isRefused_beforeTheMinimum_andOnTimedGoals() = runTest {
        val sessions = FocusSessions(dao, clock)
        val day = LocalDate.parse("2026-10-21")
        startNow(session(goal = FocusGoal.UNTIL_DONE, goalMinutes = null, focusMs = 4 * 60_000L))

        assertFalse(sessions.end(done = true, focusConfig, day))
        dao.saveProgress(1, 5 * 60_000L, 0, clock.now.toEpochMilli())
        assertTrue(sessions.end(done = true, focusConfig, day))

        startNow(session(goal = FocusGoal.FIXED_TIME, focusMs = 20 * 60_000L))
        assertFalse(sessions.end(done = true, focusConfig, day))
        assertTrue("End is always there", sessions.end(done = false, focusConfig, day))
    }

    @Test
    fun US13_noSwitching_breaks_withTheBrokenLine() = runTest {
        val sessions = FocusSessions(dao, clock)
        startNow(session(goal = FocusGoal.NO_SWITCHING))
        sessions.step(snapshot(), focusConfig, noCall, TestLabels)

        later(5)
        val broken = sessions.step(snapshot(credited = mapOf(INSTAGRAM to 2_000), front = INSTAGRAM), focusConfig, noCall, TestLabels)

        assertEquals(listOf(BubbleTrigger.FOCUS_BROKEN), broken.triggers())
        assertEquals(FocusOutcome.BROKEN, dao.latest()?.outcome)
        assertEquals(1, dao.latest()?.switches)
        assertTrue(events.events.isEmpty())
    }

    @Test
    fun D112_driftLines_inRewardAndCheckInModes_notInQuiet() = runTest {
        for ((mode, expected) in listOf(PetMode.QUIET to emptyList(), PetMode.REWARD_AT_END to listOf(BubbleTrigger.FOCUS_DRIFT), PetMode.CHECK_IN to listOf(BubbleTrigger.FOCUS_DRIFT))) {
            dao.clearAll()
            val sessions = FocusSessions(dao, clock)
            startNow(session(petMode = mode))
            sessions.step(snapshot(), focusConfig, noCall, TestLabels)

            later(5)
            val drift = sessions.step(snapshot(credited = mapOf(INSTAGRAM to 2_000), front = INSTAGRAM), focusConfig, noCall, TestLabels)

            assertEquals(mode.name, expected, drift.triggers())
            assertEquals(mode.name, FocusPhase.DRIFTING, drift.status?.phase)
            assertEquals(mode.name, 1, drift.status?.switches)
        }
    }

    @Test
    fun US13_theDriftLine_namesTheApp() = runTest {
        val sessions = FocusSessions(dao, clock)
        startNow()
        sessions.step(snapshot(), focusConfig, noCall, TestLabels)

        later(5)
        val drift = sessions.step(snapshot(credited = mapOf(INSTAGRAM to 2_000), front = INSTAGRAM), focusConfig, noCall, TestLabels)

        assertEquals(mapOf(Placeholder.APP to "App($INSTAGRAM)"), drift.offers.single().values)
    }

    @Test
    fun D112_checkIns_onlyInCheckInMode_withTheMinutes() = runTest {
        for ((mode, expected) in listOf(PetMode.QUIET to emptyList(), PetMode.REWARD_AT_END to emptyList(), PetMode.CHECK_IN to listOf(BubbleTrigger.FOCUS_CHECKIN))) {
            dao.clearAll()
            val sessions = FocusSessions(dao, clock)
            startNow(session(petMode = mode, checkinMinutes = if (mode == PetMode.CHECK_IN) 10 else null, focusMs = 10 * 60_000L - 2_000))
            sessions.step(snapshot(), focusConfig, noCall, TestLabels)

            later(5)
            val tick = sessions.step(snapshot(credited = mapOf(YOUTUBE to 5_000)), focusConfig, noCall, TestLabels)

            assertEquals(mode.name, expected, tick.triggers())
            if (expected.isNotEmpty()) assertEquals(mapOf(Placeholder.MINUTES to "10"), tick.offers.single().values)
        }
    }

    @Test
    fun A02_aCallIsReadOnlyWhileASessionRuns_andStopsTheDrift() = runTest {
        var reads = 0
        val inCall = CallState { reads++; true }
        val sessions = FocusSessions(dao, clock)
        sessions.step(snapshot(), focusConfig, inCall, TestLabels)
        assertEquals("no session, no read", 0, reads)

        startNow()
        sessions.step(snapshot(), focusConfig, inCall, TestLabels)
        later(5)
        val tick = sessions.step(snapshot(credited = mapOf(DIALER to 5_000), front = DIALER), focusConfig, inCall, TestLabels)

        assertTrue(reads > 0)
        assertTrue(tick.offers.isEmpty())
        assertEquals(FocusPhase.RUNNING, tick.status?.phase)
    }

    @Test
    fun D113_statusSaysWhenDoneIsAllowed() = runTest {
        val sessions = FocusSessions(dao, clock)
        startNow(session(goal = FocusGoal.UNTIL_DONE, goalMinutes = null, focusMs = 5 * 60_000L))

        val status = sessions.step(snapshot(), focusConfig, noCall, TestLabels).status

        assertEquals(FocusGoal.UNTIL_DONE, status?.goal)
        assertEquals(true, status?.canDone)
    }
}
