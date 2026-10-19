package com.companionstudio.nyx.home

import com.companionstudio.nyx.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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



    @Test
    fun US28_summaryIsNullUntilObserved_thenTheRepositorySummary() = runTest {
        val viewModel = main.track(HomeViewModel(FakeSummaryRepository()))
        assertNull(viewModel.summary.value)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.summary.collect {} }

        assertEquals(FakeSummaryRepository.FAKE_TODAY, viewModel.summary.value)
    }
}
