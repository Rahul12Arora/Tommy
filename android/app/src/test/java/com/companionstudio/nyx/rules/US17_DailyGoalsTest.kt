package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Which goals are on, and recording them per day for Trends (US-17, D-148). */
class US17_DailyGoalsTest {

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
    private val dao = FakeDailyGoalDao()
    private val step = GoalStep(dao)
    private val today = LocalDate.parse("2026-10-21")

    private fun config(vararg values: Pair<String, Int>) = EffectiveConfig(defaults, values.toMap())

    @Test
    fun goalsOn_limitsInHomesOrder_thenPositiveGoals_zeroIsOff() {
        val goals = goalsOn(
            config(
                SettingPaths.categoryDailyMinutes(Category.GAMES) to 0,
                SettingPaths.TOTAL_DAILY_MINUTES to 300,
                SettingPaths.GOAL_FOCUS_SESSIONS to 2,
                SettingPaths.GOAL_PRODUCTIVE_MINUTES to 60,
            ),
        )

        assertEquals(
            listOf(
                Goal(GoalKind.LIMIT, Category.SOCIAL, 60),
                Goal(GoalKind.LIMIT, Category.ENTERTAINMENT, 120),
                Goal(GoalKind.LIMIT, null, 300),
                Goal(GoalKind.FOCUS_SESSIONS, null, 2),
                Goal(GoalKind.PRODUCTIVE_MINUTES, null, 60),
            ),
            goals,
        )
    }

    @Test
    fun goalOf_roundTripsEveryKey_andSkipsUnknownOnes() {
        val goals = Category.entries.map { Goal(GoalKind.LIMIT, it, 30) } + listOf(
            Goal(GoalKind.LIMIT, null, 300),
            Goal(GoalKind.FOCUS_SESSIONS, null, 2),
            Goal(GoalKind.PRODUCTIVE_MINUTES, null, 60),
        )

        assertEquals(goals, goals.map { goalOf(it.key, it.target) })
        assertNull(goalOf("goal:something_new", 1))
    }

    @Test
    fun D148_theFirstTick_writesTodaysGoals_andAnUnchangedTick_writesNothing() = runTest {
        step.afterTick(today, config(), emptyMap(), emptyList())
        step.afterTick(today, config(), emptyMap(), emptyList())

        assertEquals(listOf("2026-10-21"), dao.writes)
        assertEquals(
            listOf(DailyGoal("2026-10-21", "limit:social", 60), DailyGoal("2026-10-21", "limit:games", 120), DailyGoal("2026-10-21", "limit:entertainment", 120)),
            dao.rows,
        )
    }

    @Test
    fun D148_aSliderChange_replacesTodaysRows_andANewDayGetsItsOwn() = runTest {
        step.afterTick(today, config(), emptyMap(), emptyList())
        step.afterTick(today, config(SettingPaths.GOAL_PRODUCTIVE_MINUTES to 45), emptyMap(), emptyList())
        step.afterTick(today.plusDays(1), config(SettingPaths.GOAL_PRODUCTIVE_MINUTES to 45), emptyMap(), emptyList())

        assertEquals(listOf("2026-10-21", "2026-10-21", "2026-10-22"), dao.writes)
        assertEquals(4, dao.rows.count { it.day == "2026-10-21" })
        assertEquals(DailyGoal("2026-10-22", GoalKeys.PRODUCTIVE_MINUTES, 45), dao.rows.last())
    }

    @Test
    fun A13_retention_deletesOldRows() = runTest {
        step.afterTick(today, config(), emptyMap(), emptyList())
        step.deleteBefore(today.plusDays(1))

        assertEquals(emptyList<DailyGoal>(), dao.rows)
    }
}
