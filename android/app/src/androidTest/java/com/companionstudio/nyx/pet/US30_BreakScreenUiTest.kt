package com.companionstudio.nyx.pet

import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.ui.theme.NyxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class US30_BreakScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val answers = mutableListOf<String>()

    private fun show(delaySeconds: Int, canSnooze: Boolean = true) {
        composeRule.setContent {
            NyxTheme {
                BreakScreen(
                    face = "😿",
                    continuousMs = 92 * 60_000L,
                    line = "Break time! Put the phone down for 5 minutes.",
                    buttonDelaySeconds = delaySeconds,
                    onTakeBreak = { answers += "break" },
                    onFiveMore = if (canSnooze) ({ answers += "more" }) else null,
                )
            }
        }
    }

    @Test
    fun D88_theButtonsWaitForTheDelay_thenWork() {
        composeRule.mainClock.autoAdvance = false
        show(delaySeconds = 2)

        composeRule.onNodeWithText("You've been on your screen for 1 h 32 min without a break").assertExists()
        composeRule.onNodeWithText("Break time! Put the phone down for 5 minutes.").assertExists()
        composeRule.onNodeWithText("Take a break").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(2_100)

        composeRule.onNodeWithText("Take a break").assertIsEnabled().performClick()
        composeRule.onNodeWithText("5 more minutes").assertIsEnabled().performClick()
        assertEquals(listOf("break", "more"), answers)
    }

    @Test
    fun D88_whileWaiting_bothButtonsSayWhen_andAreDisabled() {
        composeRule.mainClock.autoAdvance = false
        show(delaySeconds = 10)

        composeRule.mainClock.advanceTimeBy(1_100)

        composeRule.onAllNodesWithText("Available in 9 s").assertCountEquals(2).assertAll(isNotEnabled())
    }

    @Test
    fun D88_afterTheLastSnooze_onlyTakeABreakIsOffered() {
        show(delaySeconds = 0, canSnooze = false)

        composeRule.onNodeWithText("Take a break").assertIsEnabled()
        composeRule.onNodeWithText("5 more minutes").assertDoesNotExist()
    }
}
