package com.companionstudio.nyx.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.home.GoalProgress
import com.companionstudio.nyx.home.HomeScreen
import com.companionstudio.nyx.home.TodaySummary
import com.companionstudio.nyx.ui.theme.NyxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class US07_SummaryPanelTest {

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

    private val numbers = listOf(
        "Today: 3 h 12 min",
        "Productive: 1 h 5 min",
        "Doomscrolling: 47 min",
        "Social & scrolling ≤ 1 h",
        "47 min of 1 h",
        "All apps ≤ 3 h",
        "3 h 12 min of 3 h · over",
    )

    private val clicked = mutableListOf<String>()
    private val actions = PanelActions(
        hideMinutes = 30,
        onFocus = { clicked += "focus" },
        onOpenApp = { clicked += "open" },
        onHide = { clicked += "hide" },
    )

    private fun showPanel(actions: PanelActions = this.actions) {
        composeRule.setContent {
            PetWindow(today, Beside.Panel(actions), PanelLayout(Side.RIGHT, below = true, maxHeight = 2000.dp), onPetClick = {})
        }
    }

    private fun SemanticsNodeInteractionsProvider.assertShows(text: String) {
        onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun US07_panelShowsExactlyHomesNumbersAndGoals() {
        var onHome by mutableStateOf(true)
        composeRule.setContent {
            if (onHome) {
                NyxTheme { HomeScreen(today, onShowPet = null, onFocus = {}, onTrends = {}, onSettings = {}) }
            } else {
                PetWindow(today, Beside.Panel(actions), PanelLayout(Side.RIGHT, below = true, maxHeight = 2000.dp), onPetClick = {})
            }
        }
        numbers.forEach { composeRule.assertShows(it) }

        onHome = false
        numbers.forEach { composeRule.assertShows(it) }
    }

    @Test
    fun US07_buttonsCallBack_andHideShowsTheMinutes() {
        showPanel()

        composeRule.onNodeWithText("Start focus").performScrollTo().performClick()
        composeRule.onNodeWithText("Open Nyx").performScrollTo().performClick()
        composeRule.onNodeWithText("Hide for 30 min").performScrollTo().performClick()

        assertEquals(listOf("focus", "open", "hide"), clicked)
    }

    @Test
    fun focusSwitchedOff_noStartFocusButton() {
        showPanel(PanelActions(hideMinutes = 45, onFocus = null, onOpenApp = {}, onHide = {}))

        composeRule.onNodeWithText("Start focus").assertDoesNotExist()
        composeRule.assertShows("Hide for 45 min")
    }

    @Test
    fun US07_closed_showsOnlyThePet_andATapCallsBack() {
        var taps = 0
        composeRule.setContent {
            PetWindow(today, beside = null, PanelLayout(Side.LEFT, below = false, maxHeight = 500.dp), onPetClick = { taps++ })
        }

        composeRule.onAllNodesWithText("Today:", substring = true).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Nyx, your pet").assertIsDisplayed().performClick()

        assertEquals(1, taps)
    }
}
