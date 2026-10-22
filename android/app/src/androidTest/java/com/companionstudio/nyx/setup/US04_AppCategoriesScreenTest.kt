package com.companionstudio.nyx.setup

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.companionstudio.nyx.config.Category
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class US04_AppCategoriesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apps = listOf(
        AppRow("com.android.chrome", "Chrome", Category.NEUTRAL),
        AppRow("com.google.android.youtube", "YouTube", Category.ENTERTAINMENT),
        AppRow("com.instagram.android", "Instagram", Category.SOCIAL),
    )

    private fun show(rows: List<AppRow>?, query: String = "") = composeRule.setContent {
        AppCategoriesScreen(rows, query, onSearch = {}, onChoose = { _, _ -> }, loadIcon = { null }, onNext = {})
    }

    @Test
    fun BUG04_landscapeAt200PercentText_appsStillReachable() {
        composeRule.setContent {
            // A 411 × 915 dp phone turned sideways, at the largest system font
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(915.dp, 411.dp)) then DeviceConfigurationOverride.FontScale(2f)) {
                AppCategoriesScreen(apps, query = "", onSearch = {}, onChoose = { _, _ -> }, loadIcon = { null }, onNext = {})
            }
        }

        // Swipes, not performScrollToNode: on a list with no height that never finishes
        repeat(3) { composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() } }
        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun noAppsAndNoSearch_saysNoneFound() {
        show(rows = emptyList())

        composeRule.onNodeWithText("No apps found.").assertIsDisplayed()
        composeRule.onNodeWithText("No apps match.").assertDoesNotExist()
    }

    @Test
    fun searchWithNoMatches_saysNoneMatch() {
        show(rows = emptyList(), query = "zz")

        composeRule.onNodeWithText("No apps match.").assertIsDisplayed()
    }
}
