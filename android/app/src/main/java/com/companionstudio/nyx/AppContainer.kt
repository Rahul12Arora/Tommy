package com.companionstudio.nyx

import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.datastore.preferences.preferencesDataStoreFile
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigClient
import com.companionstudio.nyx.config.ConfigManager
import com.companionstudio.nyx.config.ConfigRefresher
import com.companionstudio.nyx.config.ConfigUserValues
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.data.AdjustableClock
import com.companionstudio.nyx.data.AppDatabase
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.data.SETTINGS_STORE_NAME
import com.companionstudio.nyx.data.createSettingsStore
import com.companionstudio.nyx.focus.FocusRepository
import com.companionstudio.nyx.focus.FocusSessions
import com.companionstudio.nyx.home.SummaryRepository
import com.companionstudio.nyx.home.TrackingSummaryRepository
import com.companionstudio.nyx.pet.PetSettings
import com.companionstudio.nyx.pet.PetVisibility
import com.companionstudio.nyx.rules.AudioCallState
import com.companionstudio.nyx.rules.CallState
import com.companionstudio.nyx.rules.NagLabels
import com.companionstudio.nyx.rules.ReactionSettings
import com.companionstudio.nyx.rules.Reactions
import com.companionstudio.nyx.setup.PermissionChecks
import com.companionstudio.nyx.setup.SetupSettings
import com.companionstudio.nyx.stats.StatsSettings
import com.companionstudio.nyx.tracking.AppCategoryRepository
import com.companionstudio.nyx.tracking.InstalledApps
import com.companionstudio.nyx.tracking.SystemUsageSource
import com.companionstudio.nyx.tracking.Tracker
import com.companionstudio.nyx.tracking.UsageSource
import com.companionstudio.nyx.tracking.excludedPackages
import com.companionstudio.nyx.ui.labelRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.time.Clock

/** Manual dependency injection (TECH_STACK §5). One instance, created by [PetApp]. */
class AppContainer(private val context: Context) {
    // Always the system clock, except while a debug scenario runs time faster (D-67)
    val clock = AdjustableClock()
    val dayClock = DayClock(clock)

    val configManager = ConfigManager(
        readBuiltIn = { context.assets.open(ConfigManager.BUILT_IN_ASSET).bufferedReader().use { it.readText() } },
        cacheFile = File(context.filesDir, ConfigManager.CACHE_FILE),
        // Rule IDs and paths are enough to find the problem in the document
        onRejected = { errors -> Log.w(TAG, "Cached config ignored: " + errors.joinToString { "${it.rule} at ${it.path}" }) },
    )

    // Opened lazily by Room on the first query, so creating it here costs nothing at startup
    val database = AppDatabase.create(context)

    // Internal so device tests can reset it (SetupStateRule); features use their own …Settings classes
    internal val settingsStore = createSettingsStore(
        produceFile = { context.preferencesDataStoreFile(SETTINGS_STORE_NAME) },
        onCorrupted = { e -> Log.w(TAG, "Settings file was corrupt and has been reset", e) },
    )
    val setupSettings = SetupSettings(settingsStore)
    val permissionChecks = PermissionChecks(context)
    val petSettings = PetSettings(settingsStore)
    val petVisibility = PetVisibility(petSettings, clock)
    val statsSettings = StatsSettings(settingsStore)
    val appCategories = AppCategoryRepository(database.appCategoryDao())
    val installedApps = InstalledApps(context.packageManager, context.packageName)
    val configUserValues = ConfigUserValues(settingsStore)
    val reactionSettings = ReactionSettings(settingsStore)
    val callState: CallState = AudioCallState(context.getSystemService(AudioManager::class.java))

    /**
     * What features read: the base document with the user's values on top (§14.2), updated whenever a value
     * changes or a new document is fetched (§14.12). The base is read from disk the first time something collects.
     */
    val effectiveConfig: Flow<EffectiveConfig> = combine(configManager.base, configUserValues.observe(), ::EffectiveConfig)
        .flowOn(Dispatchers.IO)

    /** Checks the server for a new config; `null` in builds without a server address (D-146). */
    val configRefresher: ConfigRefresher? = if (BuildConfig.CONFIG_BASE_URL.isEmpty()) {
        Log.i(TAG, "No config server in this build: using the cached or built-in config")
        null
    } else {
        ConfigRefresher(
            fetch = ConfigClient(BuildConfig.CONFIG_BASE_URL, BuildConfig.VERSION_NAME)::fetch,
            manager = configManager,
            // Real time, not the debug scenarios' faster clock: the 6 hours are about the server, not usage
            clock = Clock.systemUTC(),
            log = { Log.i(TAG, it) },
        )
    }

    val summaryRepository: SummaryRepository =
        TrackingSummaryRepository(database.dailyUsageDao(), database.petEventDao(), effectiveConfig, petSettings, dayClock)

    val focusSessionDao = database.focusSessionDao()
    val focusRepository = FocusRepository(focusSessionDao, effectiveConfig, dayClock, clock)

    val systemUsageSource = SystemUsageSource(context.getSystemService(UsageStatsManager::class.java))

    // The service starts a fresh tracker whenever this changes. Only the debug scenarios change it (D-67)
    internal val usageSource = MutableStateFlow<UsageSource>(systemUsageSource)

    /** One per service run: the tracker keeps its state in memory between ticks. */
    fun newTracker(source: UsageSource) = Tracker(
        source = source,
        dao = database.dailyUsageDao(),
        clock = clock,
        dayClock = dayClock,
        excluded = excludedPackages(context.packageManager, context.packageName),
        androidCategoryOf = installedApps::androidCategory,
    )

    /** One per service run, like the tracker: nags, breaks, the arbiter and the recent lines live in memory (§14.7). */
    fun newReactions(): Reactions {
        val appLabels = HashMap<String, String>()
        val labels = object : NagLabels {
            override fun app(packageName: String) = appLabels.getOrPut(packageName) { installedApps.label(packageName) }

            override fun category(category: Category?) =
                context.getString(category?.labelRes ?: R.string.limit_total_name)
        }
        return Reactions(database.petEventDao(), clock, labels, FocusSessions(focusSessionDao, clock))
    }

    private companion object {
        const val TAG = "Nyx"
    }
}
