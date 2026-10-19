package com.companionstudio.nyx

import android.content.Context
import android.util.Log
import com.companionstudio.nyx.config.ConfigManager
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.home.FakeSummaryRepository
import com.companionstudio.nyx.home.SummaryRepository
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

    private companion object {
        const val TAG = "Nyx"
    }
}
