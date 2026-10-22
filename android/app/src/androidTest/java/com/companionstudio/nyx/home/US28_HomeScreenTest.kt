package com.companionstudio.nyx.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.focus.FocusGoal
import com.companionstudio.nyx.focus.FocusPhase
import com.companionstudio.nyx.focus.FocusStatus
import com.companionstudio.nyx.rules.Goal
import com.companionstudio.nyx.rules.GoalKind
import com.companionstudio.nyx.setup.SetupPermission
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
            NyxTheme { HomeScreen(today, onShowPet = null, focus = HomeFocus(running = null, onStart = {}), onTrends = {}, onSettings = {}) }
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
    fun US17_positiveGoals_showProgress_withMetSpelledOut() {
        val withGoals = today.copy(
            positiveGoals = listOf(
                PositiveGoalProgress(Goal(GoalKind.FOCUS_SESSIONS, null, 2), used = 1, target = 2),
                PositiveGoalProgress(Goal(GoalKind.PRODUCTIVE_MINUTES, null, 60), used = 65 * 60L, target = 60 * 60L),
            ),
        )
        composeRule.setContent {
            NyxTheme { HomeScreen(withGoals, onShowPet = null, focus = HomeFocus(running = null, onStart = {}), onTrends = {}, onSettings = {}) }
        }

        composeRule.onNodeWithText("Focus sessions ≥ 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1 of 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Productive ≥ 1 h").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1 h 5 min of 1 h · met").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun D153_trendsSwitchedOff_hidesTheTrendsButton() {
        composeRule.setContent {
            NyxTheme { HomeScreen(today, onShowPet = null, focus = HomeFocus(running = null, onStart = {}), onTrends = null, onSettings = {}) }
        }

        composeRule.onNodeWithText("Trends").assertDoesNotExist()
        composeRule.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun buttonsCallTheirCallbacks() {
        val clicked = mutableListOf<String>()
        composeRule.setContent {
            NyxTheme {
                HomeScreen(
                    today,
                    onShowPet = null,
                    focus = HomeFocus(running = null, onStart = { clicked += "focus" }),
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
            NyxTheme { HomeScreen(summary = null, onShowPet = null, focus = HomeFocus(running = null, onStart = {}), onTrends = {}, onSettings = {}) }
        }

        composeRule.onNodeWithText("Today:", substring = true).assertDoesNotExist()
    }

    @Test
    fun US29_showPet_onlyWhileHidden_andCallsBack() {
        var shown = 0
        var hidden by mutableStateOf(true)
        composeRule.setContent {
            NyxTheme {
                HomeScreen(today, onShowPet = if (hidden) ({ shown++ }) else null, focus = HomeFocus(running = null, onStart = {}), onTrends = {}, onSettings = {})
            }
        }

        composeRule.onNodeWithText("The pet is hidden for now.").assertIsDisplayed()
        composeRule.onNodeWithText("Show pet").performClick()
        assertEquals(1, shown)

        hidden = false
        composeRule.onNodeWithText("Show pet").assertDoesNotExist()
    }

    @Test
    fun US12_aRunningSession_replacesStartFocus_withItsCard() {
        val ended = mutableListOf<Boolean>()
        val running = FocusStatus(FocusGoal.UNTIL_DONE, null, focusMs = 34 * 60_000L, FocusPhase.RUNNING, switches = 2, canDone = true, minDoneMinutes = 5)
        composeRule.setContent {
            NyxTheme {
                HomeScreen(
                    today,
                    onShowPet = null,
                    focus = HomeFocus(running, onStart = {}, onDone = { ended += true }, onEnd = { ended += false }),
                    onTrends = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Start focus").assertDoesNotExist()
        composeRule.onNodeWithText("34 min so far · switched away 2 times").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Done").performScrollTo().performClick()
        composeRule.onNodeWithText("End").performScrollTo().performClick()

        assertEquals(listOf(true, false), ended)
    }

    @Test
    fun E02_noLimitsOn_noEmptyGoalsHeading() {
        composeRule.setContent {
            NyxTheme { HomeScreen(today.copy(goals = emptyList()), onShowPet = null, focus = HomeFocus(running = null, onStart = {}), onTrends = {}, onSettings = {}) }
        }

        composeRule.onNodeWithText("Today: 3 h 12 min").assertIsDisplayed()
        composeRule.onNodeWithText("Goals").assertDoesNotExist()
    }

    @Test
    fun A02_goalsIsAHeading() {
        composeRule.setContent {
            NyxTheme { HomeScreen(today, onShowPet = null, focus = HomeFocus(running = null, onStart = {}), onTrends = {}, onSettings = {}) }
        }

        composeRule.onNode(hasText("Goals") and isHeading()).assertExists()
    }

    @Test
    fun US26_E01_permissionsOff_saysWhatDoesntWork_andOpensThem() {
        var fixes = 0
        composeRule.setContent {
            NyxTheme {
                HomeScreen(
                    today,
                    onShowPet = null,
                    focus = HomeFocus(running = null, onStart = {}),
                    onTrends = {},
                    onSettings = {},
                    permissions = HomePermissions(listOf(SetupPermission.USAGE_ACCESS, SetupPermission.OVERLAY), onFix = { fixes++ }),
                )
            }
        }

        composeRule.onNodeWithText("Nyx is missing a permission").assertIsDisplayed()
        composeRule.onNodeWithText("• The pet can't see your apps, so it can't track your time or react to it.").assertIsDisplayed()
        composeRule.onNodeWithText("• The pet can't appear on your screen.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open permissions").performScrollTo().performClick()

        assertEquals(1, fixes)
    }

    @Test
    fun US26_E01_nothingMissing_noPermissionCard() {
        composeRule.setContent {
            NyxTheme {
                HomeScreen(
                    today,
                    onShowPet = null,
                    focus = HomeFocus(running = null, onStart = {}),
                    onTrends = {},
                    onSettings = {},
                    permissions = HomePermissions(emptyList(), onFix = {}),
                )
            }
        }

        composeRule.onNodeWithText("Nyx is missing a permission").assertDoesNotExist()
    }

    @Test
    fun D120_focusSwitchedOff_noStartFocus() {
        composeRule.setContent {
            NyxTheme { HomeScreen(today, onShowPet = null, focus = HomeFocus(running = null, onStart = null), onTrends = {}, onSettings = {}) }
        }

        composeRule.onNodeWithText("Start focus").assertDoesNotExist()
    }
}
