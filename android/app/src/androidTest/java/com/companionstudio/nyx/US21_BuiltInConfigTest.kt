package com.companionstudio.nyx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.companionstudio.nyx.config.ConfigManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Proves the build copies shared/default-config.json into the app's assets and the app can load it. */
@RunWith(AndroidJUnit4::class)
class US21_BuiltInConfigTest {

    @Test
    fun appLoadsTheBuiltInConfigFromAssets() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PetApp
        // Nothing writes the cache until M12, so the base document has to be the built-in one
        assertFalse(File(app.filesDir, ConfigManager.CACHE_FILE).exists())

        val config = app.container.configManager.loadBase()

        assertEquals(1, config.configVersion)
        assertEquals(listOf("cat", "blob"), config.pets.map { it.id })
    }
}
