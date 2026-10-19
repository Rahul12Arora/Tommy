package com.companionstudio.nyx.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class US02_AgeCheckTest {

    @get:Rule(order = 0)
    val setupState = SetupStateRule(completed = false)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val setupSettings get() = (composeRule.activity.application as PetApp).container.setupSettings

    @Before
    fun openAgeCheck() {
        composeRule.waitUntilDisplayed("Meet Nyx")
        composeRule.onNodeWithText("Get started").performScrollTo().performClick()
    }

    @Test
    fun under18_setupStops_andNothingIsStored() {
        composeRule.onNodeWithText("No").performScrollTo().performClick()

        composeRule.onNodeWithText("Nyx is for adults").assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
        composeRule.onNodeWithText("Permissions").assertDoesNotExist()
        assertFalse(runBlocking { setupSettings.ageConfirmed.first() })
    }

    @Test
    fun under18_closeFinishesTheApp() {
        composeRule.onNodeWithText("No").performScrollTo().performClick()

        composeRule.onNodeWithText("Close").performScrollTo().performClick()

        composeRule.waitUntil { composeRule.activity.isFinishing }
    }

    @Test
    fun adult_isStored_andGoesToPermissions() {
        composeRule.onNodeWithText("Yes, I'm 18 or older").performScrollTo().performClick()

        composeRule.onNodeWithText("Permissions").assertIsDisplayed()
        assertTrue(runBlocking { setupSettings.ageConfirmed.first() })
    }
}
