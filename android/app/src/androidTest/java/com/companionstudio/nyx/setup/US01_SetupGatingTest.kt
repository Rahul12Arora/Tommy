package com.companionstudio.nyx.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.MainActivity
import com.companionstudio.nyx.SetupStateRule
import com.companionstudio.nyx.waitUntilDisplayed
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
}
