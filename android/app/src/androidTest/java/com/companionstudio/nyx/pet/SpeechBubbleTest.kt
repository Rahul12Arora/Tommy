package com.companionstudio.nyx.pet

import androidx.compose.ui.test.assertIsDisplayed
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
class SpeechBubbleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val line = "That's 1 h of Social & scrolling today. Stretch break?"

    @Test
    fun showsTheLine_andBothButtonsCallBack() {
        val clicked = mutableListOf<String>()
        composeRule.setContent {
            NyxTheme { SpeechBubble(line, onSnooze = { clicked += "snooze" }, onDismiss = { clicked += "dismiss" }) }
        }

        composeRule.onNodeWithText(line).assertIsDisplayed()
        composeRule.onNodeWithText("Snooze").performClick()
        composeRule.onNodeWithText("Dismiss").performClick()

        assertEquals(listOf("snooze", "dismiss"), clicked)
    }

    @Test
    fun US10_noSnoozeOffered_noSnoozeButton() {
        composeRule.setContent {
            NyxTheme { SpeechBubble(line, onSnooze = null, onDismiss = {}) }
        }

        composeRule.onNodeWithText("Snooze").assertDoesNotExist()
        composeRule.onNodeWithText("Dismiss").assertIsDisplayed()
    }
}
