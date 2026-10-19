package com.companionstudio.nyx.setup

import com.companionstudio.nyx.MainDispatcherRule
import com.companionstudio.nyx.data.createSettingsStore
import com.companionstudio.nyx.stats.StatsSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class US24_StatsOptInViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { createSettingsStore({ folder.root.resolve("settings.preferences_pb") }, onCorrupted = {}) }
    private val stats by lazy { StatsSettings(store) }
    private val setup by lazy { SetupSettings(store) }



    @Test
    fun optIn_isOffByDefault() = runTest {
        val viewModel = main.track(StatsOptInViewModel(stats, setup))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.optIn.collect {} }

        assertEquals(false, viewModel.optIn.first { it != null })
    }

    @Test
    fun turningItOn_isStored() = runTest {
        val viewModel = main.track(StatsOptInViewModel(stats, setup))

        viewModel.setOptIn(true).join()

        assertTrue(stats.optIn.first())
    }

    @Test
    fun finishSetup_marksSetupCompleted_withoutTouchingTheOptIn() = runTest {
        val viewModel = main.track(StatsOptInViewModel(stats, setup))

        viewModel.finishSetup()

        assertTrue(setup.completed.first())
        assertFalse(stats.optIn.first())
    }
}
