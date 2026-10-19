package com.companionstudio.nyx.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** shared/default-config.json is what the app falls back to, so it must always be valid (US-21). */
class US21_DefaultConfigTest {

    private fun defaultConfig(): NyxConfig = when (val result = parseConfig(SharedFiles.defaultConfig)) {
        is ConfigResult.Valid -> result.config
        is ConfigResult.Invalid -> throw AssertionError("default-config.json is invalid: ${result.errors}")
    }

    @Test
    fun isValid_forThisSchemaVersion_asVersion1() {
        val config = defaultConfig()

        assertEquals(SCHEMA_VERSION, config.schemaVersion)
        assertEquals(1, config.configVersion)
    }

    @Test
    fun catAndBlob_andAtLeastTwoPersonalities_areEnabled() {
        val config = defaultConfig()

        assertEquals(setOf("cat", "blob"), config.pets.filter { it.enabled }.map { it.id }.toSet())
        assertTrue(config.personalities.count { it.enabled } >= 2)
    }

    @Test
    fun everyPersonality_hasAtLeastFourLinesPerBubbleTrigger() {
        // "Never repeat the last 3" (§4) needs at least 4 lines to choose from (A-28)
        val config = defaultConfig()

        val short = config.personalities.flatMap { personality ->
            BubbleTrigger.entries.mapNotNull { trigger ->
                val count = config.lines[personality.id]?.get(trigger.id)?.get(ANY_POOL).orEmpty().size
                if (count >= 4) null else "${personality.id}.${trigger.id}: $count"
            }
        }

        assertEquals(emptyList<String>(), short)
    }
}
