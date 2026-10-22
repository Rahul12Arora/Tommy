package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.focus.FakeFocusSessionDao
import com.companionstudio.nyx.focus.FocusSessions
import com.companionstudio.nyx.focus.YOUTUBE
import com.companionstudio.nyx.focus.session
import com.companionstudio.nyx.tracking.TrackerSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/** Positive goals through [Reactions] with the default config and fake DAOs (US-17, §14.13). */
class US17_PositiveGoalsTest {

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
    private val clock = TestClock(Instant.parse("2026-10-21T10:00:00Z"))
    private val events = FakePetEventDao()
    private val sessions = FakeFocusSessionDao(events)
    private val goalDao = FakeDailyGoalDao()
    private val reactions = Reactions(events, clock, TestLabels, FocusSessions(sessions, clock), GoalStep(goalDao), Random(11))

    private fun config(productiveMinutes: Int = 0, focusSessions: Int = 0) = EffectiveConfig(
        defaults,
        mapOf(SettingPaths.GOAL_PRODUCTIVE_MINUTES to productiveMinutes, SettingPaths.GOAL_FOCUS_SESSIONS to focusSessions),
    )

    private fun pool(trigger: String) = defaults.lines.getValue("cheerful").getValue(trigger).getValue("any")

    private fun inDocs(productiveMinutes: Long, day: String = "2026-10-21") = TrackerSnapshot(
        day = LocalDate.parse(day),
        frontPackage = "com.google.android.apps.docs",
        frontCategory = Category.PRODUCTIVE,
        stretchMs = 0,
        todayMs = mapOf(Category.PRODUCTIVE to productiveMinutes * 60_000),
        continuousMs = 0,
        endedBreaks = emptyList(),
    )

    private fun inYouTube(creditedMs: Long, productiveMinutes: Long = 0) = TrackerSnapshot(
        day = LocalDate.parse("2026-10-21"),
        frontPackage = YOUTUBE,
        frontCategory = Category.ENTERTAINMENT,
        stretchMs = 0,
        todayMs = mapOf(Category.ENTERTAINMENT to 10 * 60_000L, Category.PRODUCTIVE to productiveMinutes * 60_000),
        continuousMs = 0,
        endedBreaks = emptyList(),
        creditedMs = mapOf(YOUTUBE to creditedMs),
    )

    private fun later(seconds: Long) {
        clock.now = clock.now.plus(Duration.ofSeconds(seconds))
    }

    private suspend fun tick(snapshot: TrackerSnapshot, config: EffectiveConfig, petOnScreen: Boolean = true) =
        reactions.afterTick(snapshot, config, personalityId = null, petOnScreen = petOnScreen).also { later(90) }

    private fun goalsMet() = events.events.filter { it.type == PetEventType.GOAL_MET }.map { it.day to it.subject }

    @Test
    fun US17_goalsOff_nothingIsLogged_evenWithHoursOfProductiveTime() = runTest {
        tick(inDocs(productiveMinutes = 600), config())

        assertEquals(emptyList<Pair<String, String?>>(), goalsMet())
    }

    @Test
    fun US17_productiveGoal_isMetAtItsTarget_onceADay_andRaisesMoodBy10() = runTest {
        val config = config(productiveMinutes = 30)
        val before = tick(inDocs(productiveMinutes = 29), config)
        val met = tick(inDocs(productiveMinutes = 30), config)
        val after = tick(inDocs(productiveMinutes = 45), config)

        assertEquals(listOf("2026-10-21" to GoalKeys.PRODUCTIVE_MINUTES), goalsMet())
        assertEquals(before.mood + 10, met.mood)
        assertEquals(met.mood, after.mood)
        assertTrue(met.bubble?.line in pool("goal_met"))
        assertNull(after.bubble)
    }

    @Test
    fun US17_theNextDay_theGoalCanBeMetAgain() = runTest {
        val config = config(productiveMinutes = 30)
        tick(inDocs(productiveMinutes = 30), config)
        tick(inDocs(productiveMinutes = 30, day = "2026-10-22"), config)

        assertEquals(listOf("2026-10-21" to GoalKeys.PRODUCTIVE_MINUTES, "2026-10-22" to GoalKeys.PRODUCTIVE_MINUTES), goalsMet())
    }

    @Test
    fun US17_raisingTheTargetAfterItWasMet_doesntRewardAgain() = runTest {
        tick(inDocs(productiveMinutes = 40), config(productiveMinutes = 30))
        tick(inDocs(productiveMinutes = 61), config(productiveMinutes = 60))

        assertEquals(1, goalsMet().size)
    }

    @Test
    fun US17_loweringTheTargetBelowTodaysProgress_meetsItAtTheNextTick() = runTest {
        tick(inDocs(productiveMinutes = 40), config(productiveMinutes = 60))
        assertEquals(0, goalsMet().size)

        tick(inDocs(productiveMinutes = 40), config(productiveMinutes = 30))

        assertEquals(listOf("2026-10-21" to GoalKeys.PRODUCTIVE_MINUTES), goalsMet())
    }

    @Test
    fun US17_bothGoalsMetOnOneTick_logTwoEvents_butSayOneLine() = runTest {
        events.log(PetEventType.FOCUS_COMPLETED)
        events.log(PetEventType.FOCUS_COMPLETED)
        val reaction = tick(inDocs(productiveMinutes = 30), config(productiveMinutes = 30, focusSessions = 2))

        assertEquals(setOf(GoalKeys.FOCUS_SESSIONS, GoalKeys.PRODUCTIVE_MINUTES), goalsMet().map { it.second }.toSet())
        assertTrue(reaction.bubble?.line in pool("goal_met"))
        assertNull("the second line isn't waiting either", tick(inDocs(productiveMinutes = 31), config(productiveMinutes = 30, focusSessions = 2)).bubble)
    }

    @Test
    fun D150_theSecondSessionCompletingThisTick_meetsTheFocusGoalOnTheSameTick() = runTest {
        val config = config(focusSessions = 2)
        events.log(PetEventType.FOCUS_COMPLETED)
        sessions.start(session(focusMs = 24 * 60_000L, startedAtMs = clock.now.toEpochMilli()).copy(lastFocusAtMs = clock.now.toEpochMilli()))
        tick(inYouTube(creditedMs = 5_000), config)
        assertEquals(emptyList<Pair<String, String?>>(), goalsMet())

        tick(inYouTube(creditedMs = 60_000), config)

        val types = events.events.map { it.type }
        assertEquals(2, types.count { it == PetEventType.FOCUS_COMPLETED })
        assertEquals(listOf("2026-10-21" to GoalKeys.FOCUS_SESSIONS), goalsMet())
        assertTrue("logged after the completion", types.lastIndexOf(PetEventType.GOAL_MET) > types.lastIndexOf(PetEventType.FOCUS_COMPLETED))
    }

    @Test
    fun D150_sessionsCompletedOnAnotherDay_dontCountToday() {
        val goal = Goal(GoalKind.FOCUS_SESSIONS, null, 1)
        val yesterday = PetEvent(day = "2026-10-20", atMs = 0, type = PetEventType.FOCUS_COMPLETED)

        // The engine passes today's log only; the count is over what it's given
        assertEquals(emptyList<Goal>(), positiveGoalsReached(listOf(goal), emptyMap(), logged = emptyList()))
        assertEquals(listOf(goal), positiveGoalsReached(listOf(goal), emptyMap(), logged = listOf(yesterday.copy(day = "2026-10-21"))))
    }

    @Test
    fun D154_aGoalThatBringsThePetBack_saysOnlyThePetReturnedLine() = runTest {
        repeat(7) { events.log(PetEventType.LIMIT_EXCEEDED, subject = "limit:social") }
        events.log(PetEventType.RAN_AWAY)

        val back = tick(inDocs(productiveMinutes = 30), config(productiveMinutes = 30))

        assertEquals(10, back.mood)
        assertTrue(back.bubble?.line in defaults.lines.getValue("cheerful").getValue("pet_returned").values.flatten())
        assertNull(tick(inDocs(productiveMinutes = 31), config(productiveMinutes = 30)).bubble)
    }

    @Test
    fun D24_withThePetHidden_theGoalIsStillLogged_withoutABubble() = runTest {
        val hidden = tick(inDocs(productiveMinutes = 30), config(productiveMinutes = 30), petOnScreen = false)

        assertNull(hidden.bubble)
        assertEquals(1, goalsMet().size)
    }

    @Test
    fun D25_duringAFocusSession_theGoalIsLogged_butItsLineWaits() = runTest {
        sessions.start(session(startedAtMs = clock.now.toEpochMilli()).copy(lastFocusAtMs = clock.now.toEpochMilli()))
        tick(inYouTube(creditedMs = 5_000), config(productiveMinutes = 30))
        val during = tick(inYouTube(creditedMs = 5_000, productiveMinutes = 30), config(productiveMinutes = 30))

        assertEquals(1, goalsMet().size)
        assertTrue(during.bubble == null || during.bubble?.line !in pool("goal_met"))
    }
}
