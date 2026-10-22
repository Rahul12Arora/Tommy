package com.companionstudio.nyx.home

import com.companionstudio.nyx.MainDispatcherRule
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.focus.FakeFocusSessionDao
import com.companionstudio.nyx.focus.FocusGoal
import com.companionstudio.nyx.focus.FocusOutcome
import com.companionstudio.nyx.focus.FocusRepository
import com.companionstudio.nyx.focus.session
import com.companionstudio.nyx.data.AdjustableClock
import com.companionstudio.nyx.data.createSettingsStore
import com.companionstudio.nyx.pet.PetSettings
import com.companionstudio.nyx.pet.PetVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
class HomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val today = TodaySummary(
        totalSeconds = 3600,
        productiveSeconds = 600,
        doomscrollSeconds = 1200,
        goals = listOf(GoalProgress(Category.SOCIAL, usedSeconds = 1200, targetSeconds = 3600)),
        petFace = "😸",
    )

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config

    private val petSettings by lazy {
        PetSettings(createSettingsStore({ folder.root.resolve("settings.preferences_pb") }, onCorrupted = { throw it }))
    }
    private val petVisibility by lazy { PetVisibility(petSettings, AdjustableClock()) }

    private fun viewModel() = main.track(
        HomeViewModel(
            object : SummaryRepository {
                override fun observeToday() = flowOf(today)
            },
            petVisibility,
            focusRepository,
        ),
    )

    private val focusDao = FakeFocusSessionDao()
    private val focusRepository by lazy {
        val clock = AdjustableClock()
        FocusRepository(focusDao, flowOf(EffectiveConfig(defaults, emptyMap())), DayClock(clock), clock)
    }

    @Test
    fun US28_summaryIsNullUntilObserved_thenTheRepositorySummary() = runTest {
        val viewModel = viewModel()
        assertNull(viewModel.summary.value)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.summary.collect {} }

        assertEquals(today, viewModel.summary.value)
    }

    @Test
    fun US29_petHidden_followsStorage_andShowPetClearsIt() = runTest {
        petVisibility.hide(minutes = 30)
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.petHidden.collect {} }
        viewModel.petHidden.first { it }
        assertTrue(viewModel.petHidden.value)

        viewModel.showPet().join()

        assertNull(petSettings.hiddenUntil.first())
        assertFalse(viewModel.petHidden.first { !it })
    }

    @Test
    fun US12_focus_showsTheRunningSession_andEndOnTheCardEndsIt() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.focus.collect {} }
        assertTrue("Start is offered", viewModel.focus.first { it.enabled }.running == null)

        focusDao.start(session())
        assertEquals(FocusGoal.FIXED_TIME, viewModel.focus.first { it.running != null }.running?.goal)

        viewModel.finishFocus(done = false).join()

        assertEquals(FocusOutcome.ENDED_EARLY, focusDao.latest()?.outcome)
        assertNull(viewModel.focus.first { it.running == null }.running)
    }
}
