package com.companionstudio.nyx

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.companionstudio.nyx.focus.FocusSetupRoute
import com.companionstudio.nyx.home.HomeRoute
import com.companionstudio.nyx.service.PetService
import com.companionstudio.nyx.settings.SettingsLinks
import com.companionstudio.nyx.settings.SettingsRoute
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
/** [fromRing]: opened from the pet's ring, so Start goes back to the app the user was in (D-117). */
@Serializable data class FocusSetup(val fromRing: Boolean = false, val fromApp: String? = null) : NavKey
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

// S-03 on its own, to fix a permission after setup (N-03, Settings). Continue goes back
@Serializable data object FixPermissions : NavKey

// S-04 to S-07 opened from Settings (§14.7): the same screens, Continue goes back
@Serializable data object SettingsChoosePet : NavKey
@Serializable data object SettingsAppCategories : NavKey
@Serializable data object SettingsLimits : NavKey
@Serializable data object SettingsStats : NavKey

/**
 * [open]: from N-03, S-03 sits on top of Home (§14.4); from the pet's ring, Focus setup does (§14.5), with [focusApp]
 * selected (§14.8).
 */
@Composable
fun AppNavigation(container: AppContainer, open: MainActivity.Open = MainActivity.Open.HOME, focusApp: String? = null) {
    // Read once: setup is either still to do or done for good. Nothing is drawn for the moment this takes
    val setupCompleted by produceState<Boolean?>(initialValue = null) {
        value = container.setupSettings.completed.first()
    }
    val completed = setupCompleted ?: return

    val start = when {
        !completed -> arrayOf(Welcome)
        open == MainActivity.Open.PERMISSIONS -> arrayOf(Home, FixPermissions)
        open == MainActivity.Open.FOCUS -> arrayOf(Home, FocusSetup(fromRing = true, fromApp = focusApp))
        else -> arrayOf(Home)
    }
    val backStack = rememberNavBackStack(*start)
    val activity = LocalActivity.current
    val context = LocalContext.current
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Each screen's ViewModel lives as long as its back-stack entry and is cleared when it's popped (D-55)
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Home> {
                HomeRoute(
                    summaryRepository = container.summaryRepository,
                    petVisibility = container.petVisibility,
                    focusRepository = container.focusRepository,
                    permissionChecks = container.permissionChecks,
                    onFixPermissions = { backStack.add(FixPermissions) },
                    onFocus = { backStack.add(FocusSetup()) },
                    onTrends = { backStack.add(Trends) },
                    onSettings = { backStack.add(Settings) },
                )
            }
            entry<FocusSetup> { key ->
                FocusSetupRoute(
                    installedApps = container.installedApps,
                    config = container.effectiveConfig,
                    repository = container.focusRepository,
                    fromApp = key.fromApp,
                    // From the ring: back to the app the user came from (a fresh task, so finishing returns there).
                    // From Home: back to Home, which shows the session
                    onStarted = { if (key.fromRing) activity?.finish() else backStack.removeLastOrNull() },
                )
            }
            // A placeholder until S-10 (M13) exists
            entry<Trends> { PlaceholderScreen(stringResource(R.string.trends_title)) }
            entry<Settings> {
                SettingsRoute(
                    config = container.effectiveConfig,
                    userValues = container.configUserValues,
                    reactionSettings = container.reactionSettings,
                    links = SettingsLinks(
                        onPet = { backStack.add(SettingsChoosePet) },
                        onCategories = { backStack.add(SettingsAppCategories) },
                        onLimits = { backStack.add(SettingsLimits) },
                        onStats = { backStack.add(SettingsStats) },
                        onPermissions = { backStack.add(FixPermissions) },
                    ),
                )
            }
            val back: () -> Unit = { backStack.removeLastOrNull() }
            entry<SettingsChoosePet> { ChoosePetRoute(container.effectiveConfig, container.petSettings, onNext = back) }
            entry<SettingsAppCategories> {
                AppCategoriesRoute(container.installedApps, container.effectiveConfig, container.appCategories, onNext = back)
            }
            entry<SettingsLimits> { LimitsRoute(container.effectiveConfig, container.configUserValues, onNext = back) }
            // Finish marks setup done again, which it already is
            entry<SettingsStats> { StatsOptInRoute(container.statsSettings, container.setupSettings, onFinished = back) }

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
            entry<FixPermissions> {
                PermissionsRoute(container.permissionChecks, onNext = { backStack.removeLastOrNull() })
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
                        PetService.start(context)
                        backStack.add(Home)
                        backStack.subList(0, backStack.size - 1).clear()
                    },
                )
            }
        },
    )
}
