package com.companionstudio.nyx.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.rules.Goal
import com.companionstudio.nyx.rules.GoalKind
import com.companionstudio.nyx.ui.theme.NyxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class US18_TrendsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.parse("2026-10-23")

    /** Sat 17 and Sun 18 Oct untracked, Mon 19 Oct tracked with nothing, then data up to today, Fri 23 Oct; oldest first. */
    private val week = (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        when {
            back >= 5 -> TrendDay(day, isToday = false, tracked = false, emptyMap(), emptyList())
            back == 4 -> TrendDay(day, isToday = false, tracked = true, emptyMap(), emptyList())
            else -> TrendDay(
                day,
                isToday = back == 0,
                tracked = true,
                seconds = mapOf(Category.SOCIAL to 80 * 60L, Category.PRODUCTIVE to 30 * 60L),
                goals = listOf(
                    GoalResult(Goal(GoalKind.LIMIT, Category.SOCIAL, 60), achieved = false),
                    GoalResult(Goal(GoalKind.FOCUS_SESSIONS, null, 2), achieved = true),
                ),
            )
        }
    }

    private fun show(range: TrendRange = TrendRange.WEEK, days: List<TrendDay>? = week, onRange: (TrendRange) -> Unit = {}) {
        composeRule.setContent { NyxTheme { TrendsScreen(range, days, onRange) } }
    }

    private fun scrollTo(text: String) = composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))

    @Test
    fun US18_daysBeforeTracking_sayNotTracked_andAnEmptyTrackedDay_saysZero() {
        show()

        scrollTo("0 min · no goals")
        composeRule.onNodeWithText("Mon 19 Oct").assertIsDisplayed()
        composeRule.onNodeWithText("0 min · no goals").assertIsDisplayed()
        scrollTo("Sat 17 Oct")
        composeRule.onNodeWithText("Sat 17 Oct").assertIsDisplayed()
        composeRule.onAllNodesWithText("Not tracked yet").onFirst().assertIsDisplayed()
    }

    @Test
    fun US18_aRowSaysItsTotalAndGoalsMet_andExpandsToCategoriesAndResultsInWords() {
        show()

        scrollTo("Thu 22 Oct")
        composeRule.onNodeWithText("Thu 22 Oct").performClick()

        composeRule.onNodeWithText("Social & scrolling: 1 h 20 min").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Productive: 30 min").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Social & scrolling ≤ 1 h · missed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Focus sessions ≥ 2 · met").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun D149_todayIsJudgedSoFar() {
        val todayOnTrack = week.dropLast(1) + week.last().copy(
            seconds = mapOf(Category.SOCIAL to 20 * 60L),
            goals = listOf(
                GoalResult(Goal(GoalKind.LIMIT, Category.SOCIAL, 60), achieved = true),
                GoalResult(Goal(GoalKind.PRODUCTIVE_MINUTES, null, 60), achieved = false),
            ),
        )
        show(days = todayOnTrack)

        scrollTo("Today so far")
        composeRule.onNodeWithText("Today so far").performClick()

        composeRule.onNodeWithText("20 min · goals 1 of 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Social & scrolling ≤ 1 h · on track").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Productive ≥ 1 h · not yet").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun D152_theLegendNamesEveryCategory_andTheChartHasOneSummary() {
        show()

        listOf("Social & scrolling", "Games", "Entertainment", "Productive", "Neutral")
            .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithContentDescription("Screen time per day, last 7 days. Each day is listed below.").assertIsDisplayed()
    }

    @Test
    fun US18_theRangeToggle_asksForThirtyDays() {
        val asked = mutableListOf<TrendRange>()
        show(onRange = { asked += it })

        composeRule.onNodeWithText("30 days").performClick()

        assertEquals(listOf(TrendRange.MONTH), asked)
    }

    @Test
    fun nothingTracked_saysSo() {
        show(days = week.map { it.copy(tracked = false, seconds = emptyMap(), goals = emptyList()) })

        composeRule.onNodeWithText("Nothing tracked yet. Trends fill in day by day.").assertIsDisplayed()
    }
}
