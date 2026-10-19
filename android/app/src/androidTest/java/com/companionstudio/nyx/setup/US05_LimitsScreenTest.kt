package com.companionstudio.nyx.setup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.Setting
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class US05_LimitsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sliders = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun lockedSetting_showsItsValueAndSetByNyx_withoutASlider() {
        composeRule.setContent {
            LimitsScreen(
                rows = listOf(
                    LimitRow("limits.categories.social.dailyMinutes", LimitName.CategoryDaily(Category.SOCIAL), Setting(60, 0, 240, true), 60),
                    LimitRow("limits.totalDailyMinutes", LimitName.TotalDaily, Setting(300, 0, 960, false), 300),
                ),
                onSet = { _, _ -> },
                onNext = {},
            )
        }

        composeRule.onNodeWithText("All apps per day").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("5 h").assertIsDisplayed()
        composeRule.onAllNodesWithText("Set by Nyx").assertCountEquals(1)
        composeRule.onAllNodes(sliders).assertCountEquals(1) // only the editable Social limit
    }

    @Test
    fun zeroMinutes_readsOff() {
        composeRule.setContent {
            LimitsScreen(
                rows = listOf(LimitRow("limits.totalDailyMinutes", LimitName.TotalDaily, Setting(0, 0, 960, true), 0)),
                onSet = { _, _ -> },
                onNext = {},
            )
        }

        composeRule.onNodeWithText("Off").assertIsDisplayed()
    }
}
