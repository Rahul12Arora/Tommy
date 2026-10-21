package com.companionstudio.nyx.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.home.TodaySummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetWindowBubbleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = TodaySummary(totalSeconds = 0, productiveSeconds = 0, doomscrollSeconds = 0, goals = emptyList(), petFace = "🙀")
    private val line = "I'm back! Missed you."
    private val layout = PanelLayout(Side.RIGHT, below = false, maxHeight = 600.dp)

    @Test
    fun US16_theBubbleShowsBesideThePet_withoutSnooze_andDismissCallsBack() {
        var dismissed = 0
        composeRule.setContent { PetWindow(today, Beside.Bubble(line, onDismiss = { dismissed++ }), layout, onPetClick = {}) }

        composeRule.onNodeWithContentDescription("Nyx, your pet").assertIsDisplayed()
        composeRule.onNodeWithText(line).assertIsDisplayed()
        composeRule.onNodeWithText("Snooze").assertDoesNotExist()
        composeRule.onNodeWithText("Dismiss").performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun D84_thePanelTakesTheBubblesPlace_whileOpen() {
        val actions = PanelActions(hideMinutes = 30, onFocus = null, onOpenApp = {}, onHide = {})
        var beside: Beside by mutableStateOf(Beside.Bubble(line, onDismiss = {}))
        composeRule.setContent { PetWindow(today, beside, layout, onPetClick = {}) }
        composeRule.onNodeWithText(line).assertIsDisplayed()

        beside = Beside.Panel(actions)

        composeRule.onNodeWithText(line).assertDoesNotExist()
        composeRule.onNodeWithText("Hide for 30 min").assertIsDisplayed()
    }
}
