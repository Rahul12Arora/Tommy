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
    fun D98_theDefaultDocument_spellsOutTheObstructiveDefaults_andADocumentWithoutThemGetsTheSame() {
        assertEquals(ObstructiveConfig(), defaultConfig().breaks.obstructive)
        assertTrue("minimal.json has no obstructive block", "obstructive" !in SharedFiles.fixture("valid", "minimal"))
        assertEquals(ObstructiveConfig(allowed = true, buttonDelaySeconds = 10, snoozeMinutes = 5, maxSnoozes = 3, returnAfterTakeBreakSeconds = 60), SharedFiles.minimalConfig.breaks.obstructive)
    }

    @Test
    fun D120_theDefaultDocument_spellsOutTheFocusDefaults_andADocumentWithoutThemGetsTheSame() {
        val defaults = defaultConfig().focus
        assertEquals(5, defaults.minDoneMinutes)
        assertEquals(30, defaults.abandonAfterMinutes)
        assertTrue("minimal.json has no minDoneMinutes", "minDoneMinutes" !in SharedFiles.fixture("valid", "minimal"))
        assertEquals(5, SharedFiles.minimalConfig.focus.minDoneMinutes)
        assertEquals(30, SharedFiles.minimalConfig.focus.abandonAfterMinutes)
    }

    @Test
    fun D147_theDefaultDocument_spellsOutTheGoalDefaults_andADocumentWithoutThemGetsTheSame() {
        assertEquals(GoalsConfig(), defaultConfig().goals)
        assertTrue("minimal.json has no goals block", "\"goals\"" !in SharedFiles.fixture("valid", "minimal"))
        val off = Setting(default = 0, min = 0, max = 10, userEditable = true)
        assertEquals(GoalsConfig(off, off.copy(max = 480)), SharedFiles.minimalConfig.goals)
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
