package com.companionstudio.nyx.settings

import com.companionstudio.nyx.MainDispatcherRule
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.ConfigUserValues
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.data.createSettingsStore
import com.companionstudio.nyx.rules.BreakMode
import com.companionstudio.nyx.rules.ReactionPrefs
import com.companionstudio.nyx.rules.ReactionSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config

    @Test
    fun theRows_showEffectiveValues_andTheBreakRowsGoWhenBreaksAreOff() {
        val state = settingsState(EffectiveConfig(defaults, mapOf(SettingPaths.NAG_REPEAT_MINUTES to 25)), ReactionPrefs())
        val breaksOff = defaults.copy(features = defaults.features.copy(breaks = false, idleLines = false))
        val off = settingsState(EffectiveConfig(breaksOff, emptyMap()), ReactionPrefs())

        assertEquals(25, state.nagRepeat.value)
        assertEquals(10, state.snooze.value)
        assertEquals(45, state.breakAfter?.value)
        assertEquals(0, state.resetHour.value)
        assertTrue(state.obstructiveAllowed)
        assertTrue(state.idleAvailable)
        assertNull(off.breakAfter)
        assertFalse(off.idleAvailable)
    }

    @Test
    fun D98_obstructiveForbidden_isShownAsSuch_butTheStoredChoiceIsKept() {
        val forbidden = defaults.copy(breaks = defaults.breaks.copy(obstructive = defaults.breaks.obstructive.copy(allowed = false)))

        val state = settingsState(EffectiveConfig(forbidden, emptyMap()), ReactionPrefs(mode = BreakMode.OBSTRUCTIVE))

        assertFalse(state.obstructiveAllowed)
        assertEquals(BreakMode.OBSTRUCTIVE, state.prefs.mode)
    }

    @Test
    fun valuesAreClampedAndStored_andTheModeAndIdleSwitchAreStored() = runTest {
        val store = createSettingsStore({ folder.root.resolve("settings.preferences_pb") }, onCorrupted = { throw it })
        val userValues = ConfigUserValues(store)
        val reactionSettings = ReactionSettings(store)
        val viewModel = SettingsViewModel(userValues.observe().map { EffectiveConfig(defaults, it) }, userValues, reactionSettings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        val state = viewModel.state.filterNotNull().first()

        viewModel.set(state.snooze, 99).join()
        viewModel.setMode(BreakMode.OBSTRUCTIVE).join()
        viewModel.setIdleEnabled(false).join()

        assertEquals(30, userValues.observe().first()[SettingPaths.SNOOZE_MINUTES])
        assertEquals(ReactionPrefs(BreakMode.OBSTRUCTIVE, idleEnabled = false), reactionSettings.prefs.first())
    }

    @Test
    fun reactionSettings_defaultToGentleWithIdleLines() = runTest {
        val store = createSettingsStore({ folder.root.resolve("defaults.preferences_pb") }, onCorrupted = { throw it })

        assertEquals(ReactionPrefs(BreakMode.GENTLE, idleEnabled = true), ReactionSettings(store).prefs.first())
    }
}
