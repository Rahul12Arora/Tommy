package com.companionstudio.nyx.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companionstudio.nyx.pet.PetVisibility
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(summaryRepository: SummaryRepository, private val petVisibility: PetVisibility) : ViewModel() {
    /** `null` while loading. */
    val summary: StateFlow<TodaySummary?> = summaryRepository.observeToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /** While true, Home offers to show the pet again (US-29). */
    val petHidden: StateFlow<Boolean> = petVisibility.shown().map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = false)

    /** Writes storage; the pet service follows it (D-44). */
    fun showPet(): Job = viewModelScope.launch { petVisibility.show() }
}
