package com.companionstudio.nyx.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.MainActivity
import com.companionstudio.nyx.PetApp
import com.companionstudio.nyx.SetupStateRule
import com.companionstudio.nyx.waitUntilDisplayed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A fresh install opens setup; a finished one opens Home (§14.3). The "after setup" side is US28_NavigationTest. */
@RunWith(AndroidJUnit4::class)
class US01_SetupGatingTest {

    @get:Rule(order = 0)
    val setupState = SetupStateRule(completed = false)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForTheFirstScreen() {
        composeRule.waitUntilDisplayed("Meet Nyx")
    }

    @Test
    fun freshInstall_opensWelcome() {
        composeRule.onNodeWithText("Meet Nyx").assertIsDisplayed()
        composeRule.onNodeWithText("Today: 3 h 12 min").assertDoesNotExist()
    }

    @Test
    fun recreatingTheActivity_keepsTheCurrentStep() {
        composeRule.onNodeWithText("Get started").performScrollTo().performClick()
        composeRule.onNodeWithText("Are you 18 or older?").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Are you 18 or older?").assertIsDisplayed()
    }

    @Test
    fun back_returnsToThePreviousStep() {
        composeRule.onNodeWithText("Get started").performScrollTo().performClick()
        // Waits until the step is on screen; runOnUiThread alone doesn't wait for Compose to settle
        composeRule.onNodeWithText("Are you 18 or older?").assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText("Meet Nyx").assertIsDisplayed()
    }

    @Test
    fun goingThroughEveryStep_endsOnHome_andBackLeavesTheApp() {
        composeRule.onNodeWithText("Get started").performScrollTo().performClick()
        next(onScreen = "Are you 18 or older?", tap = "Yes, I'm 18 or older")
        next(onScreen = "Permissions", tap = "Continue")
        // Whether the dialog appears depends on what the test install was granted; either way setup goes on
        if (composeRule.onAllNodesWithText("Continue anyway").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Continue anyway").performClick()
        }
        next(onScreen = "Choose your pet", tap = "Continue")
        next(onScreen = "Your apps", tap = "Continue", scroll = false) // Continue sits below the list, always on screen
        next(onScreen = "Limits", tap = "Continue")
        next(onScreen = "Help improve Nyx", tap = "Finish")

        composeRule.waitUntilDisplayed("Today: 3 h 12 min")
        val container = (composeRule.activity.application as PetApp).container
        assertTrue(runBlocking { container.setupSettings.completed.first() })

        // Setup isn't behind Home any more
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil { composeRule.activity.isFinishing }
    }

    private fun next(onScreen: String, tap: String, scroll: Boolean = true) {
        composeRule.waitUntilDisplayed(onScreen)
        val button = composeRule.onNodeWithText(tap)
        if (scroll) button.performScrollTo()
        button.performClick()
    }
}
