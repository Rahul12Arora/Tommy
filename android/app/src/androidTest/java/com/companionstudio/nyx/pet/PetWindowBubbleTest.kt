package com.companionstudio.nyx.pet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.focus.FocusGoal
import com.companionstudio.nyx.focus.FocusPhase
import com.companionstudio.nyx.focus.FocusStatus
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
        composeRule.setContent {
            PetWindow(today, glowing = false, BubbleContent(line, onSnooze = null, onDismiss = { dismissed++ }), layout, onPetClick = {})
        }

        composeRule.onNodeWithContentDescription("Nyx, your pet").assertIsDisplayed()
        composeRule.onNodeWithText(line).assertIsDisplayed()
        composeRule.onNodeWithText("Snooze").assertDoesNotExist()
        composeRule.onNodeWithText("Dismiss").performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun US10_aNagsBubble_offersSnooze() {
        var snoozed = 0
        composeRule.setContent {
            PetWindow(today, glowing = true, BubbleContent("Ding!", onSnooze = { snoozed++ }, onDismiss = {}), layout, onPetClick = {})
        }

        composeRule.onNodeWithText("Snooze").performClick()

        assertEquals(1, snoozed)
    }

    @Test
    fun US08_whileGlowing_TalkBackHearsOverALimit() {
        composeRule.setContent { PetWindow(today, glowing = true, bubble = null, layout, onPetClick = {}) }

        composeRule.onNodeWithContentDescription("Nyx, your pet. Over a limit").assertIsDisplayed()
    }

    @Test
    fun D118_duringASession_theBadgeShowsTheMinutes_andTalkBackHearsThem() {
        val focus = FocusStatus(FocusGoal.UNTIL_DONE, null, focusMs = 34 * 60_000L, FocusPhase.RUNNING, switches = 0, canDone = true, minDoneMinutes = 5)
        composeRule.setContent { PetWindow(today, glowing = false, bubble = null, layout, onPetClick = {}, focus = focus) }

        composeRule.onNodeWithText("34m", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Nyx, your pet. Focus session, 34 min so far").assertIsDisplayed()
    }
}
