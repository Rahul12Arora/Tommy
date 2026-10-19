package com.companionstudio.nyx.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class US22_EffectiveConfigTest {

    private val minimal = SharedFiles.minimalConfig
    private val snooze = SettingPaths.SNOOZE_MINUTES

    /** minimal.json with the snooze setting replaced; its default there is 10 in 5..30. */
    private fun withSnooze(min: Int = 5, max: Int = 30, userEditable: Boolean = true) =
        minimal.copy(nags = minimal.nags.copy(snoozeMinutes = Setting(default = 10, min = min, max = max, userEditable = userEditable)))

    @Test
    fun noUserValue_usesTheDefault() {
        assertEquals(10, EffectiveConfig(withSnooze(), userValues = emptyMap()).valueOf(snooze))
    }

    @Test
    fun userValueInsideTheRange_isUsed() {
        assertEquals(25, EffectiveConfig(withSnooze(), mapOf(snooze to 25)).valueOf(snooze))
    }

    @Test
    fun userValueOutsideTheRange_isClamped() {
        assertEquals(30, EffectiveConfig(withSnooze(), mapOf(snooze to 99)).valueOf(snooze))
        assertEquals(5, EffectiveConfig(withSnooze(), mapOf(snooze to 1)).valueOf(snooze))
    }

    @Test
    fun rangeNarrowed_clampsTheValue_andWideningAgainRestoresIt() {
        val stored = mapOf(snooze to 25)

        assertEquals(15, EffectiveConfig(withSnooze(max = 15), stored).valueOf(snooze))
        // The stored value isn't rewritten, so the old choice comes back
        assertEquals(25, EffectiveConfig(withSnooze(max = 30), stored).valueOf(snooze))
    }

    @Test
    fun lockedSetting_ignoresTheUserValue() {
        assertEquals(10, EffectiveConfig(withSnooze(userEditable = false), mapOf(snooze to 25)).valueOf(snooze))
    }

    @Test
    fun settingTheDocumentDoesntHave_isNull() {
        // minimal.json only has a Social limit
        val effective = EffectiveConfig(minimal, mapOf(SettingPaths.categoryDailyMinutes(Category.PRODUCTIVE) to 30))

        assertNull(effective.valueOf(SettingPaths.categoryDailyMinutes(Category.PRODUCTIVE)))
        assertNull(effective.valueOf("no.such.path"))
    }

    @Test
    fun settings_listEveryPath_inDisplayOrder() {
        assertEquals(
            listOf(
                "day.resetHour",
                "limits.categories.social.dailyMinutes",
                "limits.totalDailyMinutes",
                "limits.stretch.minutes",
                "nags.repeatMinutes",
                "nags.snoozeMinutes",
                "breaks.afterMinutes",
                "focus.durationMinutes",
                "focus.checkinMinutes",
            ),
            EffectiveConfig(minimal, emptyMap()).settings.keys.toList(),
        )
    }

    @Test
    fun categoryLimits_followTheCategoryOrder() {
        val limit = CategoryLimit(Setting(default = 60, min = 0, max = 240, userEditable = true))
        val config = minimal.copy(limits = minimal.limits.copy(categories = mapOf(Category.GAMES to limit, Category.SOCIAL to limit)))

        assertEquals(
            listOf("limits.categories.social.dailyMinutes", "limits.categories.games.dailyMinutes"),
            config.settingsByPath().keys.filter { it.startsWith("limits.categories.") },
        )
    }
}
