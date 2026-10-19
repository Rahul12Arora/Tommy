package com.companionstudio.nyx

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class US28_NavigationTest {

    // A finished setup, so the launcher opens Home
    @get:Rule(order = 0)
    val setupState = SetupStateRule(completed = true)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsHome_settingsOpens_backReturnsHome() {
        composeRule.waitUntilDisplayed("Goals") // Home, whatever today's real numbers are

        composeRule.onNodeWithText("Settings").performScrollTo().performClick()
        composeRule.onNodeWithText("Not built yet.").assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("Goals").assertIsDisplayed()
        composeRule.onNodeWithText("Not built yet.").assertDoesNotExist()
    }
}
