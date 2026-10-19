package com.companionstudio.nyx

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText

/**
 * The first screen is drawn only after AppNavigation has read the setup flag, which Compose's idling doesn't track.
 * On a cold start the window can also still be opening. So the first screen is waited for, not assumed.
 */
fun ComposeTestRule.waitUntilDisplayed(text: String) {
    waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithText(text).assertIsDisplayed() }.isSuccess }
}
