package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.focus.FakeFocusSessionDao
import com.companionstudio.nyx.focus.FocusSessions
import com.companionstudio.nyx.focus.YOUTUBE
import com.companionstudio.nyx.focus.session
import com.companionstudio.nyx.tracking.EndedBreak
import com.companionstudio.nyx.tracking.TrackerSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/** What a focus session holds back and what it lets through at the end, through [Reactions] (§14.8, D-25, D-115). */
class US13_FocusHoldTest {

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
    private val config = EffectiveConfig(defaults, emptyMap())
    private val clock = TestClock(Instant.parse("2026-10-21T10:00:00Z"))
    private val events = FakePetEventDao()
    private val sessions = FakeFocusSessionDao(events)
    private val reactions = Reactions(events, clock, TestLabels, FocusSessions(sessions, clock), GoalStep(FakeDailyGoalDao()), Random(3))

    private fun pool(trigger: String) = defaults.lines.getValue("cheerful").getValue(trigger).getValue("any")

    /** YouTube in front: Entertainment, whose default limit is 2 h. */
    private fun inYouTube(
        entertainmentMinutes: Long = 10,
        continuousMinutes: Long = 10,
        creditedMs: Long = 5_000,
        day: String = "2026-10-21",
        breaks: List<EndedBreak> = emptyList(),
    ) = TrackerSnapshot(
        day = LocalDate.parse(day),
        frontPackage = YOUTUBE,
        frontCategory = Category.ENTERTAINMENT,
        stretchMs = 0,
        todayMs = mapOf(Category.ENTERTAINMENT to entertainmentMinutes * 60_000),
        continuousMs = continuousMinutes * 60_000,
        endedBreaks = breaks,
        creditedMs = mapOf(YOUTUBE to creditedMs),
    )

    private suspend fun startSession(focusMinutesSoFar: Long = 0, day: String = "2026-10-21") {
        sessions.start(session(focusMs = focusMinutesSoFar * 60_000, startedAtMs = clock.now.toEpochMilli(), day = day).copy(lastFocusAtMs = clock.now.toEpochMilli()))
    }

    private fun later(seconds: Long) {
        clock.now = clock.now.plus(Duration.ofSeconds(seconds))
    }

    private suspend fun tick(snapshot: TrackerSnapshot, petOnScreen: Boolean = true, prefs: ReactionPrefs = ReactionPrefs()) =
        reactions.afterTick(snapshot, config, personalityId = null, petOnScreen = petOnScreen, prefs = prefs)

    @Test
    fun D115_duringASession_aNagIsLoggedButHeld_noBubbleNoGlowNoN04() = runTest {
        later(1)
        startSession()

        val start = tick(inYouTube(entertainmentMinutes = 120))
        later(90)
        val hidden = tick(inYouTube(entertainmentMinutes = 122), petOnScreen = false)

        assertTrue(start.bubble?.line in pool("focus_started"))
        assertFalse(start.glowing)
        assertEquals(listOf("limit:entertainment"), events.events.filter { it.type == PetEventType.LIMIT_EXCEEDED }.map { it.subject })
        assertTrue(hidden.nagNotifications.isEmpty())
        assertFalse(hidden.glowing)
        assertNotNull(hidden.status.focus)
    }

    @Test
    fun D115_atTheEnd_theEndLineComesFirst_thenTheHeldNag_andTheGlowReturns() = runTest {
        later(1)
        startSession(focusMinutesSoFar = 25)
        tick(inYouTube(entertainmentMinutes = 120))
        later(90)
        val end = tick(inYouTube(entertainmentMinutes = 122))
        later(30)
        val tooSoon = tick(inYouTube(entertainmentMinutes = 122))
        later(30)
        val nag = tick(inYouTube(entertainmentMinutes = 123))

        assertTrue(end.bubble?.line in pool("focus_completed"))
        assertFalse(end.glowing)
        assertNull(end.status.focus)
        assertNull("the gap after the end line", tooSoon.bubble)
        assertTrue(tooSoon.glowing)
        assertEquals("limit:entertainment", nag.bubble?.snoozeKey)
        // 70 − 10 for the limit + 15 for the session
        assertEquals(75, nag.mood)
    }

    @Test
    fun D115_aHeldNag_isWithdrawn_ifItsLimitStoppedNaggingByTheEnd() = runTest {
        later(1)
        startSession(focusMinutesSoFar = 25)
        tick(inYouTube(entertainmentMinutes = 120))
        later(90)
        tick(inYouTube(entertainmentMinutes = 122).copy(frontPackage = null, frontCategory = null))
        later(120)
        val after = tick(inYouTube(entertainmentMinutes = 122).copy(frontPackage = null, frontCategory = null, creditedMs = emptyMap()))

        assertNull(after.bubble)
        assertFalse(after.glowing)
    }

    @Test
    fun D115_breakRemindersWait_andAStillDueOneFiresOnceTheSessionEnds() = runTest {
        later(1)
        startSession(focusMinutesSoFar = 20)
        tick(inYouTube(continuousMinutes = 44))
        later(60)
        val due = tick(inYouTube(continuousMinutes = 45))
        later(60)
        tick(inYouTube(continuousMinutes = 46, creditedMs = 5 * 60_000))
        later(5)
        val after = tick(inYouTube(continuousMinutes = 46))

        assertNull(due.breakNotification)
        assertFalse(due.bubble?.line in pool("break_due").map { it.replace("{minutes}", "5") })
        assertNotNull("the reminder fires once the session is over", after.breakNotification)
    }

    @Test
    fun D89_obstructive_theBreakScreenWaitsForTheEndToo() = runTest {
        val obstructive = ReactionPrefs(mode = BreakMode.OBSTRUCTIVE)
        later(1)
        startSession(focusMinutesSoFar = 20)
        tick(inYouTube(continuousMinutes = 44), prefs = obstructive)
        later(60)
        val due = tick(inYouTube(continuousMinutes = 45), prefs = obstructive)
        later(60)
        tick(inYouTube(continuousMinutes = 46, creditedMs = 5 * 60_000), prefs = obstructive)
        later(5)
        val after = tick(inYouTube(continuousMinutes = 46), prefs = obstructive)

        assertNull(due.breakScreen)
        assertNull(due.breakNotification)
        assertTrue(after.breakScreen is BreakScreenChange.Show)
    }

    @Test
    fun D115_aBreakTakenDuringTheSession_meansNoReminderAtTheEnd() = runTest {
        later(1)
        startSession(focusMinutesSoFar = 20)
        tick(inYouTube(continuousMinutes = 44))
        later(60)
        tick(inYouTube(continuousMinutes = 45))
        later(600)
        val back = tick(inYouTube(continuousMinutes = 0, creditedMs = 0, breaks = listOf(EndedBreak(clock.now, continuousBeforeMs = 45 * 60_000))))
        later(5)
        tick(inYouTube(continuousMinutes = 1, creditedMs = 5 * 60_000))
        later(5)
        val after = tick(inYouTube(continuousMinutes = 1))

        assertTrue(back.breakTaken)
        assertNull(after.breakNotification)
    }

    @Test
    fun US16_finishingBringsARunawayPetBack_withOnlyTheFocusLine() = runTest {
        repeat(7) { events.log(PetEventType.LIMIT_EXCEEDED, subject = "limit:x$it") }
        events.log(PetEventType.RAN_AWAY)
        later(1)
        startSession(focusMinutesSoFar = 25)
        tick(inYouTube())
        later(90)
        val end = tick(inYouTube())
        later(120)
        val after = tick(inYouTube())

        assertEquals(15, end.mood)
        assertEquals(PetEventType.PET_RETURNED, events.events.last().type)
        assertTrue(end.bubble?.line in pool("focus_completed"))
        assertNull("no pet_returned line after it", after.bubble)
    }

    @Test
    fun A25_aDayResetDoesntStopTheSession_andTheRewardGoesOnTheNewDay() = runTest {
        later(1)
        startSession(focusMinutesSoFar = 24)
        tick(inYouTube())
        later(60)
        val nextDay = tick(inYouTube(day = "2026-10-22", creditedMs = 30_000))
        later(60)
        tick(inYouTube(day = "2026-10-22", creditedMs = 60_000))

        assertNotNull("still running after the reset", nextDay.status.focus)
        assertEquals("2026-10-21", sessions.latest()?.day)
        assertEquals("2026-10-22", events.events.single { it.type == PetEventType.FOCUS_COMPLETED }.day)
    }

    @Test
    fun US07_theRingStatusCarriesTheSession_andTicksAreFastOnlyWithTheScreenOn() = runTest {
        later(1)
        startSession()
        val running = tick(inYouTube())
        later(5)
        val off = tick(inYouTube(creditedMs = 0).copy(screenOn = false, frontPackage = null, frontCategory = null))

        assertEquals(YOUTUBE, running.status.frontPackage)
        assertNotNull(running.status.focus)
        assertTrue(running.focusRunning)
        assertFalse(off.focusRunning)
        assertNotNull(off.status.focus)
    }

    @Test
    fun D118_endFromTheRing_isNoticedByTheNextTick() = runTest {
        later(1)
        startSession()
        tick(inYouTube())

        assertTrue(reactions.endFocus(done = false, config))
        later(5)
        val next = tick(inYouTube())

        assertNull(next.status.focus)
        assertFalse(next.focusRunning)
        assertTrue(events.events.none { it.type == PetEventType.FOCUS_COMPLETED })
    }
}
