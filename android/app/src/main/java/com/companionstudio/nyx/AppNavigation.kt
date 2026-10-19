package com.companionstudio.nyx

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.companionstudio.nyx.home.HomeRoute
import com.companionstudio.nyx.ui.PlaceholderScreen
import kotlinx.serialization.Serializable

// Keys are @Serializable so the back stack survives rotation and process death
@Serializable data object Home : NavKey
@Serializable data object FocusSetup : NavKey
@Serializable data object Trends : NavKey
@Serializable data object Settings : NavKey

@Composable
fun AppNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(Home)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Home> {
                HomeRoute(
                    summaryRepository = container.summaryRepository,
                    onFocus = { backStack.add(FocusSetup) },
                    onTrends = { backStack.add(Trends) },
                    onSettings = { backStack.add(Settings) },
                )
            }
            // Placeholders until S-09 (M8), S-10 (M13) and S-11 (M7) exist
            entry<FocusSetup> { PlaceholderScreen(stringResource(R.string.focus_setup_title)) }
            entry<Trends> { PlaceholderScreen(stringResource(R.string.trends_title)) }
            entry<Settings> { PlaceholderScreen(stringResource(R.string.settings_title)) }
        },
    )
}
