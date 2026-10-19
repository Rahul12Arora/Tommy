package com.companionstudio.nyx.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun US28_summaryIsNullUntilObserved_thenTheRepositorySummary() = runTest {
        val viewModel = HomeViewModel(FakeSummaryRepository())
        assertNull(viewModel.summary.value)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.summary.collect {} }

        assertEquals(FakeSummaryRepository.FAKE_TODAY, viewModel.summary.value)
    }
}
