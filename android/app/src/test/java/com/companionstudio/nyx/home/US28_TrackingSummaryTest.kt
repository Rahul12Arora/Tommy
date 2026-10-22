package com.companionstudio.nyx.home

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.pet.PetChoice
import com.companionstudio.nyx.rules.Goal
import com.companionstudio.nyx.rules.GoalKind
import com.companionstudio.nyx.rules.PetEvent
import com.companionstudio.nyx.rules.PetEventType
import com.companionstudio.nyx.tracking.DailyUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class US28_TrackingSummaryTest {

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
    private val noChoice = PetChoice(petId = null, personalityId = null)
    private val noEvents = emptyList<PetEvent>()

    private fun row(packageName: String, category: Category, minutes: Long) =
        DailyUsage("2026-10-21", packageName, category, durationMs = minutes * 60_000)

    private val rows = listOf(
        row("com.instagram.android", Category.SOCIAL, 30),
        row("com.zhiliaoapp.musically", Category.SOCIAL, 15),
        row("com.google.android.youtube", Category.ENTERTAINMENT, 20),
        row("com.google.android.apps.docs", Category.PRODUCTIVE, 10),
        row("com.android.chrome", Category.NEUTRAL, 5),
    )

    @Test
    fun totals_productive_andDoomscrolling_comeFromTheRows() {
        val summary = todaySummary(rows, noEvents, EffectiveConfig(defaults, emptyMap()), noChoice)

        assertEquals(80 * 60L, summary.totalSeconds)
        assertEquals(10 * 60L, summary.productiveSeconds)
        assertEquals(45 * 60L, summary.doomscrollSeconds) // Social only (A-15)
    }

    @Test
    fun D68_goalsAreTheLimitsThatAreOn_inCategoryOrder() {
        val summary = todaySummary(rows, noEvents, EffectiveConfig(defaults, emptyMap()), noChoice)

        assertEquals(
            listOf(
                GoalProgress(Category.SOCIAL, usedSeconds = 45 * 60, targetSeconds = 60 * 60),
                GoalProgress(Category.GAMES, usedSeconds = 0, targetSeconds = 120 * 60),
                GoalProgress(Category.ENTERTAINMENT, usedSeconds = 20 * 60, targetSeconds = 120 * 60),
            ),
            summary.goals,
        ) // the total limit is off by default
    }

    @Test
    fun D68_userValuesApply_aLimitTurnedOffHasNoGoal_andTheTotalShowsWhenOn() {
        val userValues = mapOf(
            SettingPaths.categoryDailyMinutes(Category.SOCIAL) to 0,
            SettingPaths.categoryDailyMinutes(Category.GAMES) to 30,
            SettingPaths.TOTAL_DAILY_MINUTES to 60,
        )

        val goals = todaySummary(rows, noEvents, EffectiveConfig(defaults, userValues), noChoice).goals

        assertEquals(
            listOf(
                GoalProgress(Category.GAMES, usedSeconds = 0, targetSeconds = 30 * 60),
                GoalProgress(Category.ENTERTAINMENT, usedSeconds = 20 * 60, targetSeconds = 120 * 60),
                GoalProgress(category = null, usedSeconds = 80 * 60, targetSeconds = 60 * 60),
            ),
            goals,
        )
        assertEquals(true, goals.last().isOver)
    }

    @Test
    fun US17_positiveGoals_showTodaysProgress_sessionsFromTheLog_productiveFromTheRows() {
        val config = EffectiveConfig(defaults, mapOf(SettingPaths.GOAL_FOCUS_SESSIONS to 2, SettingPaths.GOAL_PRODUCTIVE_MINUTES to 10))
        val completed = PetEvent(id = 1, day = "2026-10-21", atMs = 0, type = PetEventType.FOCUS_COMPLETED)

        val summary = todaySummary(rows, listOf(completed), config, noChoice)

        assertEquals(
            listOf(
                PositiveGoalProgress(Goal(GoalKind.FOCUS_SESSIONS, null, 2), used = 1, target = 2),
                PositiveGoalProgress(Goal(GoalKind.PRODUCTIVE_MINUTES, null, 10), used = 10 * 60L, target = 10 * 60L),
            ),
            summary.positiveGoals,
        )
        assertEquals(listOf(false, true), summary.positiveGoals.map { it.isMet })
        assertEquals("limit goals stay as they were", 3, summary.goals.size)
    }

    @Test
    fun US17_positiveGoalsOff_areNotShown() {
        assertEquals(emptyList<PositiveGoalProgress>(), todaySummary(rows, noEvents, EffectiveConfig(defaults, emptyMap()), noChoice).positiveGoals)
    }

    @Test
    fun emptyDay_isAllZeros() {
        val summary = todaySummary(emptyList(), noEvents, EffectiveConfig(defaults, emptyMap()), noChoice)

        assertEquals(0L, summary.totalSeconds)
        assertEquals(0L, summary.goals.first().usedSeconds)
    }

    @Test
    fun faceIsTheChosenPetsStartingFace_orTheFirstEnabledPet() {
        val config = EffectiveConfig(defaults, emptyMap())

        assertEquals("😄", todaySummary(rows, noEvents, config, PetChoice(petId = "blob", personalityId = "calm")).petFace)
        assertEquals("😸", todaySummary(rows, noEvents, config, noChoice).petFace)
        assertEquals("😸", todaySummary(rows, noEvents, config, PetChoice(petId = "gone", personalityId = null)).petFace)
    }

    @Test
    fun US15_US16_theFaceFollowsTodaysMood_downToRunningAway() {
        val config = EffectiveConfig(defaults, emptyMap())
        fun doomscrolled(times: Int) = List(times) { PetEvent(id = it + 1L, day = "2026-10-21", atMs = it * 1000L, type = PetEventType.DOOMSCROLL_10MIN) }
        val cat = PetChoice(petId = "cat", personalityId = null)
        val blob = PetChoice(petId = "blob", personalityId = null)

        assertEquals("😺", todaySummary(rows, doomscrolled(1), config, cat).petFace) // 67, ok
        assertEquals("😾", todaySummary(rows, doomscrolled(12), config, cat).petFace) // 34, grumpy
        assertEquals("🙀", todaySummary(rows, doomscrolled(23), config, cat).petFace) // 1, sick
        assertEquals("🐾", todaySummary(rows, doomscrolled(24), config, cat).petFace) // 0, ran away
        assertEquals("💨", todaySummary(rows, doomscrolled(24), config, blob).petFace)
    }
}
