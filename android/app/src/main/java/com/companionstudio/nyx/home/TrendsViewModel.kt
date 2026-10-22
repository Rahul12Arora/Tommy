package com.companionstudio.nyx.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class TrendsViewModel(repository: TrendsRepository) : ViewModel() {
    private val selected = MutableStateFlow(TrendRange.WEEK)
    val range: StateFlow<TrendRange> = selected.asStateFlow()

    /** `null` while loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val days: StateFlow<List<TrendDay>?> = selected.flatMapLatest(repository::observe)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun show(range: TrendRange) {
        selected.value = range
    }
}
