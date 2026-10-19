package com.companionstudio.nyx.home

import com.companionstudio.nyx.MainDispatcherRule
import com.companionstudio.nyx.config.Category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val today = TodaySummary(
        totalSeconds = 3600,
        productiveSeconds = 600,
        doomscrollSeconds = 1200,
        goals = listOf(GoalProgress(Category.SOCIAL, usedSeconds = 1200, targetSeconds = 3600)),
        petFace = "😸",
    )

    @Test
    fun US28_summaryIsNullUntilObserved_thenTheRepositorySummary() = runTest {
        val viewModel = main.track(HomeViewModel(object : SummaryRepository {
            override fun observeToday() = flowOf(today)
        }))
        assertNull(viewModel.summary.value)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.summary.collect {} }

        assertEquals(today, viewModel.summary.value)
    }
}
