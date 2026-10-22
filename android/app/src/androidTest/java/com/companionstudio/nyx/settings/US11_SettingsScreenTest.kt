package com.companionstudio.nyx.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.config.Setting
import com.companionstudio.nyx.rules.BreakMode
import com.companionstudio.nyx.rules.ReactionPrefs
import com.companionstudio.nyx.ui.theme.NyxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class US11_SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clicked = mutableListOf<String>()
    private val links = SettingsLinks(
        onPet = { clicked += "pet" },
        onCategories = { clicked += "categories" },
        onLimits = { clicked += "limits" },
        onStats = { clicked += "stats" },
        onPermissions = { clicked += "permissions" },
    )

    private fun state(allowed: Boolean = true, snoozeLocked: Boolean = false) = SettingsState(
        nagRepeat = SettingRow("nags.repeatMinutes", Setting(10, 5, 30, true), 10),
        snooze = SettingRow("nags.snoozeMinutes", Setting(15, 5, 30, !snoozeLocked), 15),
        breakAfter = SettingRow("breaks.afterMinutes", Setting(45, 20, 120, true), 45),
        resetHour = SettingRow("day.resetHour", Setting(0, 0, 6, true), 4),
        prefs = ReactionPrefs(mode = BreakMode.OBSTRUCTIVE),
        obstructiveAllowed = allowed,
        buttonDelaySeconds = 10,
        idleAvailable = true,
    )

    private fun show(state: SettingsState, modes: MutableList<BreakMode> = mutableListOf()) {
        composeRule.setContent { NyxTheme { SettingsScreen(state, links, SettingsActions({ _, _ -> }, { modes += it }, {})) } }
    }

    @Test
    fun US11_showsEveryRow_withItsValue() {
        show(state())

        listOf("Nag again every", "10 min", "Snooze for", "15 min", "Break reminder after", "45 min", "Day starts at", "04:00", "Idle chatter")
            .forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun D16_aLockedSetting_isReadOnly() {
        show(state(snoozeLocked = true))

        composeRule.onNodeWithText("Set by Nyx").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun D98_obstructive_canBePicked_whenAllowed() {
        val modes = mutableListOf<BreakMode>()
        show(state(), modes)

        composeRule.onNodeWithText("Obstructive").performScrollTo().assertIsSelected()
        composeRule.onNodeWithText("Gentle").performScrollTo().performClick()

        assertEquals(listOf(BreakMode.GENTLE), modes)
    }

    @Test
    fun D98_obstructiveForbidden_isDisabled_andGentleShowsAsChosen() {
        show(state(allowed = false))

        composeRule.onNodeWithText("Turned off for everyone.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Obstructive").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Gentle").assertIsSelected()
    }

    @Test
    fun theLinks_openTheirScreens() {
        show(state())

        listOf("Pet and personality", "App categories", "Limits", "Share anonymous stats", "Permissions")
            .forEach { composeRule.onNodeWithText(it).performScrollTo().performClick() }

        assertEquals(listOf("pet", "categories", "limits", "stats", "permissions"), clicked)
    }
}
