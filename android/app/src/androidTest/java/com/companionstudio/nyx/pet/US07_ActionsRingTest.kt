package com.companionstudio.nyx.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.focus.FocusGoal
import com.companionstudio.nyx.focus.FocusPhase
import com.companionstudio.nyx.focus.FocusStatus
import com.companionstudio.nyx.home.GoalProgress
import com.companionstudio.nyx.home.TodaySummary
import com.companionstudio.nyx.rules.LimitKeys
import com.companionstudio.nyx.rules.NagPhase
import com.companionstudio.nyx.rules.NagState
import com.companionstudio.nyx.rules.RingStatus
import com.companionstudio.nyx.ui.theme.NyxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class US07_ActionsRingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = TodaySummary(
        totalSeconds = 3 * 3600 + 12 * 60,
        productiveSeconds = 3600 + 5 * 60,
        doomscrollSeconds = 47 * 60,
        goals = listOf(
            GoalProgress(Category.SOCIAL, usedSeconds = 52 * 60, targetSeconds = 45 * 60),
            GoalProgress(Category.GAMES, usedSeconds = 10 * 60, targetSeconds = 120 * 60),
        ),
        petFace = "😾",
    )
    private val status = RingStatus(
        continuousMs = 38 * 60_000L,
        breakAfterMinutes = 45,
        stretchMs = 12 * 60_000L,
        stretchLimitMinutes = 30,
        nags = mapOf(LimitKeys.category(Category.SOCIAL) to NagState(NagPhase.SNOOZED, snoozedUntil = Instant.parse("2026-10-21T14:30:00Z"))),
    )
    private val clicked = mutableListOf<String>()
    private val actions = RingActions(
        hideMinutes = 30,
        onFocus = { clicked += "focus" },
        onOpenApp = { clicked += "open" },
        onHide = { clicked += "hide" },
    )

    private fun showRing(actions: RingActions = this.actions, onClose: () -> Unit = {}) {
        composeRule.setContent {
            NyxTheme { ActionsRing(today, status, glowing = true, actions, Side.RIGHT, maxWidth = 400.dp, onPetClick = {}, onClose = onClose) }
        }
    }

    @Test
    fun US07_everySlotSaysWhatItMeans_withHomesNumbers() {
        showRing()

        listOf(
            "Screen time today: 3 h 12 min",
            "Productive 1 h 5 min · doomscrolling 47 min",
            "Social & scrolling: 52 min of 45 min · over",
            "On screen for 38 min without a break · reminder at 45 min",
            "Start focus",
            "Open Nyx",
            "Hide for 30 min",
        ).forEach { composeRule.onNodeWithContentDescription(it).assertIsDisplayed() }
        composeRule.onNodeWithContentDescription("Nyx, your pet. Over a limit").assertIsDisplayed()
    }

    @Test
    fun US07_tappingAValueSlot_showsItsCaption() {
        showRing()

        composeRule.onNodeWithContentDescription("Screen time today: 3 h 12 min").performClick()

        composeRule.onNodeWithText("Screen time today: 3 h 12 min").assertIsDisplayed()
    }

    @Test
    fun US07_actionsCallBack_andALongPressShowsTheirCaption() {
        showRing()

        composeRule.onNodeWithContentDescription("Start focus").performClick()
        composeRule.onNodeWithContentDescription("Open Nyx").performClick()
        composeRule.onNodeWithContentDescription("Hide for 30 min").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Hide for 30 min").performClick()

        assertEquals(listOf("focus", "open", "hide"), clicked)
        composeRule.onNodeWithText("Hide for 30 min").assertIsDisplayed()
    }

    @Test
    fun D90_theLimitsSlot_opensTheListOfEveryLimit_andBackReturns() {
        showRing()

        composeRule.onNodeWithContentDescription("Social & scrolling: 52 min of 45 min · over").performClick()

        composeRule.onNodeWithText("Social & scrolling ≤ 45 min").assertIsDisplayed()
        composeRule.onNodeWithText("52 min of 45 min · over").assertIsDisplayed()
        composeRule.onNodeWithText("Games ≤ 2 h").assertIsDisplayed()
        composeRule.onNodeWithText("Without a break: 12 min of 30 min").assertIsDisplayed()
        composeRule.onNodeWithText("Snoozed until", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithContentDescription("Open Nyx").assertIsDisplayed()
    }

    @Test
    fun focusSwitchedOff_noStartFocusSlot() {
        showRing(RingActions(hideMinutes = 45, onFocus = null, onOpenApp = {}, onHide = {}))

        composeRule.onNodeWithContentDescription("Start focus").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Hide for 45 min").assertIsDisplayed()
    }

    @Test
    fun D92_aTapOnTheEmptySpace_closesTheRing() {
        var closed = 0
        showRing(onClose = { closed++ })

        // The top-left corner: no slot, no caption, no pet
        composeRule.onRoot().performTouchInput { click(Offset(5f, 5f)) }

        assertEquals(1, closed)
    }

    @Test
    fun D118_aRunningSession_putsItsTimerInStartFocussSlot_withTheCard() {
        val ended = mutableListOf<Boolean>()
        val focusing = status.copy(
            focus = FocusStatus(FocusGoal.FIXED_TIME, 25, focusMs = 13 * 60_000L, FocusPhase.DRIFTING, switches = 1, canDone = false, minDoneMinutes = 5),
        )
        composeRule.setContent {
            NyxTheme {
                ActionsRing(
                    today,
                    focusing,
                    glowing = false,
                    RingActions(30, onFocus = {}, onOpenApp = {}, onHide = {}, onEndFocus = { ended += it }),
                    Side.RIGHT,
                    maxWidth = 400.dp,
                    onPetClick = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Start focus").assertDoesNotExist()
        // The ring slot and the pet's badge
        val timers = composeRule.onAllNodesWithText("!12m", useUnmergedTree = true).assertCountEquals(2)
        timers[0].assertIsDisplayed()
        timers[1].assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Nyx, your pet. Focus session, 12 min left · switched away once · away from your focus apps")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Focus: 12 min left · switched away once · away from your focus apps").performClick()

        composeRule.onNodeWithText("Focus session").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertDoesNotExist()
        composeRule.onNodeWithText("End").performClick()
        assertEquals(listOf(false), ended)
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithContentDescription("Open Nyx").assertIsDisplayed()
    }
}
