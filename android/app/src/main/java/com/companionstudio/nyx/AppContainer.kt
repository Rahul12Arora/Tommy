package com.companionstudio.nyx

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStoreFile
import com.companionstudio.nyx.config.ConfigManager
import com.companionstudio.nyx.config.ConfigUserValues
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.data.AppDatabase
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.data.SETTINGS_STORE_NAME
import com.companionstudio.nyx.data.createSettingsStore
import com.companionstudio.nyx.home.FakeSummaryRepository
import com.companionstudio.nyx.home.SummaryRepository
import com.companionstudio.nyx.pet.PetSettings
import com.companionstudio.nyx.setup.PermissionChecks
import com.companionstudio.nyx.setup.SetupSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File

/** Manual dependency injection (TECH_STACK §5). One instance, created by [PetApp]. */
class AppContainer(context: Context) {
    val dayClock = DayClock()
    val summaryRepository: SummaryRepository = FakeSummaryRepository() // real data in M4

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
    val configUserValues = ConfigUserValues(settingsStore)

    // Read from disk once, the first time something collects. M12 swaps this for a flow that follows the fetched document
    private val baseConfig by lazy { configManager.loadBase() }

    /** What features read: the base document with the user's values on top (§14.2), updated whenever a value changes. */
    val effectiveConfig: Flow<EffectiveConfig> = configUserValues.observe()
        .map { userValues -> EffectiveConfig(baseConfig, userValues) }
        .flowOn(Dispatchers.IO)

    private companion object {
        const val TAG = "Nyx"
    }
}
