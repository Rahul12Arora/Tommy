package com.companionstudio.nyx.focus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.config.Setting
import com.companionstudio.nyx.ui.theme.NyxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class US12_FocusSetupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val youTube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"
    private val duration = Setting(25, 5, 180, userEditable = true)
    private val checkin = Setting(10, 5, 30, userEditable = true)

    private fun state(choices: FocusChoices) = FocusSetupState(
        choices = choices,
        duration = duration,
        checkin = checkin,
        apps = listOf(
            AppChoice(youTube, "YouTube", selected = youTube in choices.apps),
            AppChoice(instagram, "Instagram", selected = instagram in choices.apps),
        ),
        canStart = choices.apps.isNotEmpty(),
    )

    @Test
    fun US12_sectionsFollowTheGoalAndMode_andStartWorksOnceAnAppIsPicked() {
        var choices by mutableStateOf(FocusChoices(FocusGoal.FIXED_TIME, 25, emptySet(), PetMode.CHECK_IN, 10))
        var started = 0
        val actions = FocusSetupActions(
            onGoal = { choices = choices.copy(goal = it) },
            onMinutes = {},
            onToggleApp = { app -> choices = choices.copy(apps = choices.apps.let { if (app in it) it - app else it + app }) },
            onPetMode = { choices = choices.copy(petMode = it) },
            onCheckinMinutes = {},
            onSearch = {},
            onStart = { started++ },
        )
        composeRule.setContent { NyxTheme { FocusSetupScreen(state(choices), query = "", actions, loadIcon = { null }) } }

        composeRule.onNodeWithText("Length").assertIsDisplayed()
        composeRule.onNodeWithText("Start").assertIsNotEnabled()

        composeRule.onNodeWithText("Until I'm done").performClick()
        composeRule.onNodeWithText("Length").assertDoesNotExist()

        val list = composeRule.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Check in every"))
        composeRule.onNodeWithText("Check in every").assertIsDisplayed()
        list.performScrollToNode(hasText("Quiet presence"))
        composeRule.onNodeWithText("Quiet presence").performClick()
        composeRule.onNodeWithText("Check in every").assertDoesNotExist()

        list.performScrollToNode(hasText("YouTube"))
        composeRule.onNode(isToggleable() and hasText("YouTube")).assertIsOff().performClick()
        composeRule.onNode(isToggleable() and hasText("YouTube")).assertIsOn()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()

        assertEquals(1, started)
        assertEquals(setOf(youTube), choices.apps)
    }

    @Test
    fun US12_searchTypesIntoTheField() {
        var typed = ""
        val actions = FocusSetupActions({}, {}, {}, {}, {}, onSearch = { typed = it }, onStart = {})
        composeRule.setContent {
            NyxTheme { FocusSetupScreen(state(FocusChoices(FocusGoal.FIXED_TIME, 25, emptySet(), PetMode.QUIET, 10)), "", actions, loadIcon = { null }) }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Search apps"))
        composeRule.onNodeWithText("Search apps").performTextInput("tube")

        assertEquals("tube", typed)
    }
}
