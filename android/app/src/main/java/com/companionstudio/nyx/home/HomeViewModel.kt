package com.companionstudio.nyx.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(summaryRepository: SummaryRepository) : ViewModel() {
    /** `null` while loading. */
    val summary: StateFlow<TodaySummary?> = summaryRepository.observeToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)
}
