package com.companionstudio.nyx.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone side of the shared contract (D-39, D-53); the backend runs the same files in M10. */
class US21_ConfigFixturesTest {

    @Test
    fun everyValidFixture_isAccepted() {
        val files = SharedFiles.fixtures("valid")
        assertTrue("no fixtures found in ${SharedFiles.dir}/fixtures/valid", files.isNotEmpty())

        val failures = files.mapNotNull { file ->
            (parseConfig(file.readText()) as? ConfigResult.Invalid)?.let { "${file.name}: ${it.errors}" }
        }

        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun everyInvalidFixture_isRejectedWithExactlyTheRuleInItsName() {
        val files = SharedFiles.fixtures("invalid")
        assertTrue("no fixtures found in ${SharedFiles.dir}/fixtures/invalid", files.isNotEmpty())

        val failures = files.mapNotNull { file ->
            val expectedRule = file.name.substringBefore("--")
            when (val result = parseConfig(file.readText())) {
                is ConfigResult.Valid -> "${file.name}: accepted"
                is ConfigResult.Invalid ->
                    if (result.errors.map { it.rule }.toSet() == setOf(expectedRule)) null else "${file.name}: ${result.errors}"
            }
        }

        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun everyRule_hasAnInvalidFixture() {
        val rules = listOf(
            ConfigRule.SCHEMA, ConfigRule.SETTING_RANGE, ConfigRule.TICK_ORDER, ConfigRule.DUPLICATE_ID,
            ConfigRule.MOOD_BANDS_COVERAGE, ConfigRule.PET_FACES, ConfigRule.LINE_REFERENCES,
            ConfigRule.LINE_COVERAGE, ConfigRule.LINE_PLACEHOLDERS, ConfigRule.NOTHING_ENABLED,
        )
        val covered = SharedFiles.fixtures("invalid").map { it.name.substringBefore("--") }.toSet()

        assertEquals(emptyList<String>(), rules.filterNot { it in covered })
    }
}
