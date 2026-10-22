package com.companionstudio.nyx.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The screen on its own, with a given status. Granting through system Settings is checked by hand (manual QA). */
@RunWith(AndroidJUnit4::class)
class US01_PermissionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var nextCalls = 0
    private val granted = mutableListOf<SetupPermission>()
    private var appSettingsOpened = 0

    private fun show(status: PermissionStatus, brand: PhoneBrand = PhoneBrand.OTHER) = composeRule.setContent {
        PermissionsScreen(
            status = status,
            brand = brand,
            onGrant = { granted += it },
            onOpenAppSettings = { appSettingsOpened++ },
            onNext = { nextCalls++ },
        )
    }

    @Test
    fun grantedRowsSayDone_otherRowsOfferSettings() {
        show(PermissionStatus(setOf(SetupPermission.USAGE_ACCESS, SetupPermission.BATTERY)))

        composeRule.onAllNodesWithText("Done ✓").assertCountEquals(2)
        composeRule.onAllNodesWithText("Not done yet").assertCountEquals(2)
        composeRule.onAllNodesWithText("Open settings").assertCountEquals(2)
    }

    @Test
    fun openSettings_asksForThatRowsPermission() {
        show(PermissionStatus(SetupPermission.entries.toSet() - SetupPermission.OVERLAY))

        composeRule.onNodeWithText("Open settings").performScrollTo().performClick()

        assertEquals(listOf(SetupPermission.OVERLAY), granted)
    }

    @Test
    fun everythingGranted_continueGoesStraightOn() {
        show(PermissionStatus(SetupPermission.entries.toSet()))

        composeRule.onNodeWithText("Continue").performScrollTo().performClick()

        assertEquals(1, nextCalls)
        composeRule.onNodeWithText("Some things won't work").assertDoesNotExist()
    }

    @Test
    fun skipped_continueListsWhatWontWork_thenContinueAnyway() {
        show(PermissionStatus(setOf(SetupPermission.USAGE_ACCESS, SetupPermission.NOTIFICATIONS)))

        composeRule.onNodeWithText("Continue").performScrollTo().performClick()

        composeRule.onNodeWithText("Some things won't work").assertIsDisplayed()
        composeRule.onNodeWithText("• The pet can't appear on your screen.").assertIsDisplayed()
        composeRule.onNodeWithText("• Your phone may stop the pet in the background.").assertIsDisplayed()
        composeRule.onNodeWithText("• No break reminders or alerts outside the pet.").assertDoesNotExist()
        assertEquals(0, nextCalls)

        composeRule.onNodeWithText("Continue anyway").performClick()

        assertEquals(1, nextCalls)
    }

    @Test
    fun A01_everythingSkipped_theListScrolls_soLargeTextNeverCutsItOff() {
        show(PermissionStatus(emptySet()))

        composeRule.onNodeWithText("Continue").performScrollTo().performClick()

        composeRule.onNodeWithText("You can turn these on later from Settings.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Continue anyway").assertIsDisplayed()
    }

    @Test
    fun skipped_goBackStaysOnTheScreen() {
        show(PermissionStatus(emptySet()))

        composeRule.onNodeWithText("Continue").performScrollTo().performClick()
        composeRule.onNodeWithText("Go back").performClick()

        composeRule.onNodeWithText("Some things won't work").assertDoesNotExist()
        assertEquals(0, nextCalls)
    }

    @Test
    fun brandSteps_showForThatBrand_andOpenAppSettings() {
        show(PermissionStatus(emptySet()), brand = PhoneBrand.SAMSUNG)

        composeRule.onNodeWithText("1. Open Nyx's app settings below, tap Battery and choose Unrestricted.")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open Nyx's app settings").performScrollTo().performClick()

        assertEquals(1, appSettingsOpened)
    }
}
