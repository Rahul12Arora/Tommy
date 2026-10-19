package com.companionstudio.nyx.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class US21_ConfigManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val builtIn = SharedFiles.fixture("valid", "minimal") // configVersion 1
    private val rejected = mutableListOf<List<ConfigError>>()

    private fun cacheFile(): File = folder.root.resolve(ConfigManager.CACHE_FILE)

    private fun manager(builtInText: String = builtIn) =
        ConfigManager(readBuiltIn = { builtInText }, cacheFile = cacheFile(), onRejected = { rejected += it })

    private fun cache(text: String) = cacheFile().writeText(text)

    private fun rejectedRules() = rejected.map { errors -> errors.map { it.rule }.toSet() }

    @Test
    fun noCache_usesBuiltIn() {
        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(emptyList<Set<String>>(), rejectedRules())
    }

    @Test
    fun validCache_isUsed() {
        cache(builtIn.replace("\"configVersion\": 1", "\"configVersion\": 7"))

        assertEquals(7, manager().loadBase().configVersion)
        assertEquals(emptyList<Set<String>>(), rejectedRules())
    }

    @Test
    fun invalidCache_fallsBackToBuiltIn_andIsReported() {
        cache(SharedFiles.fixture("invalid", "setting-range--default-above-max"))

        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(listOf(setOf(ConfigRule.SETTING_RANGE)), rejectedRules())
    }

    @Test
    fun cacheThatIsNotJson_fallsBackToBuiltIn() {
        cache("{ half a document")

        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(listOf(setOf(ConfigRule.SCHEMA)), rejectedRules())
    }

    @Test
    fun cacheForAnotherSchemaVersion_fallsBackToBuiltIn() {
        cache(SharedFiles.fixture("invalid", "schema--wrong-schema-version"))

        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(listOf(setOf(ConfigRule.SCHEMA)), rejectedRules())
    }

    @Test
    fun unreadableCache_fallsBackToBuiltIn_andIsReported() {
        cache(builtIn)
        cacheFile().setReadable(false)
        // Root can read any file, so there's nothing to test there
        assumeFalse("running as root", cacheFile().canRead())

        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(listOf(setOf(ConfigManager.CACHE_UNREADABLE)), rejectedRules())
    }

    @Test
    fun invalidBuiltIn_throws() {
        // A broken build, not a broken server: US21_DefaultConfigTest stops this from shipping
        assertThrows(IllegalStateException::class.java) {
            manager(builtInText = SharedFiles.fixture("invalid", "tick-order--fast-above-slow")).loadBase()
        }
    }
}
