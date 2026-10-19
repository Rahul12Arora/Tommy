package com.companionstudio.nyx.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.ui.theme.NyxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class US28_HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = TodaySummary(
        totalSeconds = 3 * 3600 + 12 * 60,
        productiveSeconds = 3600 + 5 * 60,
        doomscrollSeconds = 47 * 60,
        goals = listOf(
            GoalProgress(Category.SOCIAL, usedSeconds = 47 * 60, targetSeconds = 60 * 60),
            GoalProgress(category = null, usedSeconds = 3 * 3600 + 12 * 60, targetSeconds = 3 * 3600),
        ),
        petFace = "😸",
    )

    @Test
    fun showsFaceTotalsAndLimitGoals_withOverSpelledOut() {
        composeRule.setContent {
            NyxTheme { HomeScreen(today, onFocus = {}, onTrends = {}, onSettings = {}) }
        }

        composeRule.onNodeWithText("😸").assertIsDisplayed()
        composeRule.onNodeWithText("Today: 3 h 12 min").assertIsDisplayed()
        composeRule.onNodeWithText("Productive: 1 h 5 min").assertIsDisplayed()
        composeRule.onNodeWithText("Doomscrolling: 47 min").assertIsDisplayed()
        composeRule.onNodeWithText("Social & scrolling ≤ 1 h").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("47 min of 1 h").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("All apps ≤ 3 h").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("3 h 12 min of 3 h · over").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun buttonsCallTheirCallbacks() {
        val clicked = mutableListOf<String>()
        composeRule.setContent {
            NyxTheme {
                HomeScreen(
                    today,
                    onFocus = { clicked += "focus" },
                    onTrends = { clicked += "trends" },
                    onSettings = { clicked += "settings" },
                )
            }
        }

        composeRule.onNodeWithText("Start focus").performScrollTo().performClick()
        composeRule.onNodeWithText("Trends").performScrollTo().performClick()
        composeRule.onNodeWithText("Settings").performScrollTo().performClick()

        assertEquals(listOf("focus", "trends", "settings"), clicked)
    }

    @Test
    fun loading_showsNoTotals() {
        composeRule.setContent {
            NyxTheme { HomeScreen(summary = null, onFocus = {}, onTrends = {}, onSettings = {}) }
        }

        composeRule.onNodeWithText("Today:", substring = true).assertDoesNotExist()
    }
}
