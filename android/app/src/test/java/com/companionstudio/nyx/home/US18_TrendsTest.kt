package com.companionstudio.nyx.home

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.rules.DailyGoal
import com.companionstudio.nyx.rules.FakeDailyGoalDao
import com.companionstudio.nyx.rules.FakePetEventDao
import com.companionstudio.nyx.rules.Goal
import com.companionstudio.nyx.rules.GoalKeys
import com.companionstudio.nyx.rules.GoalKind
import com.companionstudio.nyx.rules.PetEvent
import com.companionstudio.nyx.rules.PetEventType
import com.companionstudio.nyx.tracking.CategoryTotal
import com.companionstudio.nyx.tracking.DailyUsage
import com.companionstudio.nyx.tracking.DailyUsageDao
import com.companionstudio.nyx.tracking.DayCategoryTotal
import com.companionstudio.nyx.tracking.TrackingCursor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** S-10's days (US-18, §14.13): ranges, before tracking, per category, and each day judged by its own goals. */
class US18_TrendsTest {

    private val today = LocalDate.parse("2026-10-23")

    private fun total(day: String, category: Category, minutes: Long) = DayCategoryTotal(day, category, minutes * 60_000)

    private fun event(day: String, type: PetEventType, subject: String?) = PetEvent(day = day, atMs = 0, type = type, subject = subject)

    private fun days(
        range: TrendRange = TrendRange.WEEK,
        firstDay: String? = "2026-10-01",
        totals: List<DayCategoryTotal> = emptyList(),
        goals: List<DailyGoal> = emptyList(),
        events: List<PetEvent> = emptyList(),
    ) = trendDays(today, range, firstDay?.let(LocalDate::parse), totals, goals, events)

    @Test
    fun US18_sevenAndThirtyDays_oldestFirst_endingToday() {
        val week = days()
        val month = days(TrendRange.MONTH)

        assertEquals(7, week.size)
        assertEquals(LocalDate.parse("2026-10-17"), week.first().day)
        assertEquals(today, week.last().day)
        assertEquals(listOf(true), week.filter { it.isToday }.map { it.day == today })
        assertEquals(30, month.size)
        assertEquals(LocalDate.parse("2026-09-24"), month.first().day)
    }

    @Test
    fun US18_daysBeforeTracking_areEmpty_notZero_andATrackedDayWithNoRows_isZero() {
        val week = days(firstDay = "2026-10-20")

        assertEquals(listOf(false, false, false, true, true, true, true), week.map { it.tracked })
        assertEquals(0L, week[3].totalSeconds)
        assertTrue(days(firstDay = null).none { it.tracked })
    }

    @Test
    fun US18_timePerCategory_isSummedPerDay() {
        val week = days(
            totals = listOf(
                total("2026-10-22", Category.SOCIAL, 30),
                total("2026-10-22", Category.PRODUCTIVE, 45),
                total("2026-10-23", Category.SOCIAL, 10),
            ),
        )

        assertEquals(mapOf(Category.SOCIAL to 1800L, Category.PRODUCTIVE to 2700L), week[5].seconds)
        assertEquals(75 * 60L, week[5].totalSeconds)
        assertEquals(mapOf(Category.SOCIAL to 600L), week[6].seconds)
    }

    @Test
    fun CR05_aLimitGoal_isMissedOnlyByALimitExceededWithItsKey_thatDay() {
        val goals = listOf("2026-10-21", "2026-10-22").map { DailyGoal(it, "limit:social", 60) }
        val week = days(
            goals = goals,
            events = listOf(
                event("2026-10-21", PetEventType.LIMIT_EXCEEDED, "limit:social"),
                event("2026-10-22", PetEventType.LIMIT_EXCEEDED, "limit:games"),
            ),
        )

        assertEquals(listOf(GoalResult(Goal(GoalKind.LIMIT, Category.SOCIAL, 60), achieved = false)), week[4].goals)
        assertEquals(listOf(GoalResult(Goal(GoalKind.LIMIT, Category.SOCIAL, 60), achieved = true)), week[5].goals)
    }

    @Test
    fun US17_aPositiveGoal_isMetByAGoalMetWithItsKey_aSubjectlessOneMatchesNothing() {
        val goals = listOf("2026-10-21", "2026-10-22").map { DailyGoal(it, GoalKeys.PRODUCTIVE_MINUTES, 60) }
        val week = days(
            goals = goals,
            events = listOf(
                event("2026-10-21", PetEventType.GOAL_MET, GoalKeys.PRODUCTIVE_MINUTES),
                event("2026-10-22", PetEventType.GOAL_MET, null),
            ),
        )

        assertEquals(listOf(true), week[4].goals.map { it.achieved })
        assertEquals(listOf(false), week[5].goals.map { it.achieved })
    }

    @Test
    fun D148_eachDayShowsOnlyItsOwnGoals_inHomesOrder_unknownKeysSkipped() {
        val week = days(
            goals = listOf(
                DailyGoal("2026-10-22", GoalKeys.FOCUS_SESSIONS, 2),
                DailyGoal("2026-10-22", "limit:total", 300),
                DailyGoal("2026-10-22", "goal:from_a_newer_build", 1),
                DailyGoal("2026-10-22", "limit:social", 60),
                DailyGoal("2026-10-21", "limit:social", 45),
            ),
        )

        assertEquals(listOf("limit:social", "limit:total", GoalKeys.FOCUS_SESSIONS), week[5].goals.map { it.goal.key })
        assertEquals(2, week[5].goalsAchieved) // the two limits are kept, the focus goal isn't met
        assertEquals(listOf(45), week[4].goals.map { it.goal.target })
        assertTrue(week[3].goals.isEmpty())
    }

    @Test
    fun US18_theRepository_decidesTodayByTheResetHour_andCombinesTheTables() = runTest {
        val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
        val config = flowOf(EffectiveConfig(defaults, mapOf(SettingPaths.RESET_HOUR to 4)))
        // 03:30 UTC on the 23rd belongs to the 22nd with a 04:00 reset
        val clock = Clock.fixed(Instant.parse("2026-10-23T03:30:00Z"), ZoneOffset.UTC)
        val dayClock = DayClock(clock) { ZoneId.of("UTC") }
        val goals = FakeDailyGoalDao().apply { rows += DailyGoal("2026-10-20", "limit:social", 60) }
        val events = FakePetEventDao().apply { log(PetEventType.LIMIT_EXCEEDED, "limit:social", day = "2026-10-20") }
        val usage = object : DailyUsageDao() {
            override fun observeTotalsSince(from: String): Flow<List<DayCategoryTotal>> = flowOf(listOf(total("2026-10-22", Category.GAMES, 5)))
            override suspend fun creditedUntilMs(): Long? = null
            override fun observeDay(day: String) = flowOf(emptyList<DailyUsage>())
            override suspend fun categoryTotals(day: String) = emptyList<CategoryTotal>()
            override suspend fun deleteBefore(day: String) = Unit
            override suspend fun addTo(day: String, packageName: String, category: String, durationMs: Long) = 0
            override suspend fun insert(row: DailyUsage) = Unit
            override suspend fun setCursor(cursor: TrackingCursor) = Unit
            override suspend fun deleteAllUsage() = Unit
            override suspend fun deleteCursor() = Unit
        }

        val week = TrendsRepository(usage, events, goals, config, dayClock).observe(TrendRange.WEEK).first()

        assertEquals(LocalDate.parse("2026-10-22"), week.last().day)
        assertTrue(week.last().isToday)
        assertEquals(300L, week.last().totalSeconds)
        assertFalse("the 19th is before the first goal row", week[3].tracked)
        assertEquals(listOf(false), week[4].goals.map { it.achieved })
    }
}
