package com.companionstudio.nyx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.companionstudio.nyx.config.ConfigManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Proves the build copies shared/default-config.json into the app's assets and the app can load it. */
@RunWith(AndroidJUnit4::class)
class US21_BuiltInConfigTest {

    @Test
    fun appLoadsTheBuiltInConfigFromAssets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Since M12 the app's own cache may hold a fetched document, so this reads the asset with no cache
        val manager = ConfigManager(
            readBuiltIn = { context.assets.open(ConfigManager.BUILT_IN_ASSET).bufferedReader().use { it.readText() } },
            cacheFile = File(context.cacheDir, "no-such-config-cache.json"),
            onRejected = { error("No cache to reject: $it") },
        )

        val config = manager.loadBase()

        assertEquals(1, config.configVersion)
        assertEquals(listOf("cat", "blob"), config.pets.map { it.id })
    }
}
