package com.companionstudio.nyx

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStoreFile
import com.companionstudio.nyx.config.ConfigManager
import com.companionstudio.nyx.data.AppDatabase
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.data.SETTINGS_STORE_NAME
import com.companionstudio.nyx.data.createSettingsStore
import com.companionstudio.nyx.home.FakeSummaryRepository
import com.companionstudio.nyx.home.SummaryRepository
import com.companionstudio.nyx.setup.SetupSettings
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

    private val settingsStore = createSettingsStore(
        produceFile = { context.preferencesDataStoreFile(SETTINGS_STORE_NAME) },
        onCorrupted = { e -> Log.w(TAG, "Settings file was corrupt and has been reset", e) },
    )
    val setupSettings = SetupSettings(settingsStore)

    private companion object {
        const val TAG = "Nyx"
    }
}
