package com.companionstudio.nyx.config

import com.companionstudio.nyx.data.createSettingsStore
import com.companionstudio.nyx.pet.PetSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class US22_ConfigUserValuesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { createSettingsStore({ folder.root.resolve("settings.preferences_pb") }, onCorrupted = {}) }
    private val values by lazy { ConfigUserValues(store) }
    private val snooze = Setting(default = 10, min = 5, max = 30, userEditable = true)

    @Test
    fun nothingSet_nothingStored() = runTest {
        assertEquals(emptyMap<String, Int>(), values.observe().first())
    }

    @Test
    fun set_thenObserve_givesTheValueByPath() = runTest {
        values.set(SettingPaths.SNOOZE_MINUTES, snooze, 20)

        assertEquals(mapOf(SettingPaths.SNOOZE_MINUTES to 20), values.observe().first())
    }

    @Test
    fun valuesOutsideTheRange_areClampedBeforeStoring() = runTest {
        values.set(SettingPaths.SNOOZE_MINUTES, snooze, 99)
        assertEquals(30, values.observe().first()[SettingPaths.SNOOZE_MINUTES])

        values.set(SettingPaths.SNOOZE_MINUTES, snooze, 1)
        assertEquals(5, values.observe().first()[SettingPaths.SNOOZE_MINUTES])
    }

    @Test
    fun otherFeaturesKeys_areIgnored() = runTest {
        PetSettings(store).save(petId = "cat", personalityId = "calm")

        assertEquals(emptyMap<String, Int>(), values.observe().first())
    }

    @Test
    fun effectiveConfig_followsAStoredValue() = runTest {
        val minimal = SharedFiles.minimalConfig
        val path = SettingPaths.SNOOZE_MINUTES
        values.set(path, minimal.nags.snoozeMinutes, minimal.nags.snoozeMinutes.max)

        val effective = EffectiveConfig(minimal, values.observe().first())

        assertEquals(minimal.nags.snoozeMinutes.max, effective.valueOf(path))
    }
}
