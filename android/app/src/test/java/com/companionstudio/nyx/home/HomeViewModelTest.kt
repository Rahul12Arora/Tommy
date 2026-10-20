package com.companionstudio.nyx.home

import com.companionstudio.nyx.MainDispatcherRule
import com.companionstudio.nyx.config.Category
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
        ),
    )

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
}
