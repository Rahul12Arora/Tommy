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

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsHome_settingsOpens_backReturnsHome() {
        composeRule.onNodeWithText("Today: 3 h 12 min").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performScrollTo().performClick()
        composeRule.onNodeWithText("Not built yet.").assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("Today: 3 h 12 min").assertIsDisplayed()
        composeRule.onNodeWithText("Not built yet.").assertDoesNotExist()
    }
}
