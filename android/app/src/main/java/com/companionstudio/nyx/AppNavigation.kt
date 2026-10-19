package com.companionstudio.nyx

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.companionstudio.nyx.home.HomeRoute
import com.companionstudio.nyx.setup.AdultsOnlyScreen
import com.companionstudio.nyx.setup.AgeCheckRoute
import com.companionstudio.nyx.setup.AppCategoriesRoute
import com.companionstudio.nyx.setup.ChoosePetRoute
import com.companionstudio.nyx.setup.LimitsRoute
import com.companionstudio.nyx.setup.PermissionsRoute
import com.companionstudio.nyx.setup.StatsOptInRoute
import com.companionstudio.nyx.setup.WelcomeScreen
import com.companionstudio.nyx.ui.PlaceholderScreen
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

// Keys are @Serializable so the back stack survives rotation and process death
@Serializable data object Home : NavKey
@Serializable data object FocusSetup : NavKey
@Serializable data object Trends : NavKey
@Serializable data object Settings : NavKey

// Setup, S-01 to S-07 (docs/ARCHITECTURE.md §14.3)
@Serializable data object Welcome : NavKey
@Serializable data object AgeCheck : NavKey
@Serializable data object AdultsOnly : NavKey
@Serializable data object Permissions : NavKey
@Serializable data object ChoosePet : NavKey
@Serializable data object AppCategories : NavKey
@Serializable data object Limits : NavKey
@Serializable data object StatsOptIn : NavKey

@Composable
fun AppNavigation(container: AppContainer) {
    // Read once: setup is either still to do or done for good. Nothing is drawn for the moment this takes
    val setupCompleted by produceState<Boolean?>(initialValue = null) {
        value = container.setupSettings.completed.first()
    }
    val completed = setupCompleted ?: return

    val backStack = rememberNavBackStack(if (completed) Home else Welcome)
    val activity = LocalActivity.current
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Each screen's ViewModel lives as long as its back-stack entry and is cleared when it's popped (D-55)
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
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

            entry<Welcome> { WelcomeScreen(onStart = { backStack.add(AgeCheck) }) }
            entry<AgeCheck> {
                AgeCheckRoute(
                    setupSettings = container.setupSettings,
                    onAdult = { backStack.add(Permissions) },
                    onUnder18 = { backStack.add(AdultsOnly) },
                )
            }
            entry<AdultsOnly> { AdultsOnlyScreen(onClose = { activity?.finish() }) }
            entry<Permissions> {
                PermissionsRoute(container.permissionChecks, onNext = { backStack.add(ChoosePet) })
            }
            entry<ChoosePet> {
                ChoosePetRoute(container.effectiveConfig, container.petSettings, onNext = { backStack.add(AppCategories) })
            }
            entry<AppCategories> {
                AppCategoriesRoute(
                    installedApps = container.installedApps,
                    config = container.effectiveConfig,
                    repository = container.appCategories,
                    onNext = { backStack.add(Limits) },
                )
            }
            entry<Limits> {
                LimitsRoute(container.effectiveConfig, container.configUserValues, onNext = { backStack.add(StatsOptIn) })
            }
            entry<StatsOptIn> {
                StatsOptInRoute(
                    statsSettings = container.statsSettings,
                    setupSettings = container.setupSettings,
                    // Home replaces the whole setup stack, so Back on Home leaves the app instead of going into setup
                    onFinished = {
                        backStack.add(Home)
                        backStack.subList(0, backStack.size - 1).clear()
                    },
                )
            }
        },
    )
}
