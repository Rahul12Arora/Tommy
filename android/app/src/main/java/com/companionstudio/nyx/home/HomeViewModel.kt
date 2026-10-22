package com.companionstudio.nyx.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companionstudio.nyx.focus.FocusRepository
import com.companionstudio.nyx.focus.FocusStatus
import com.companionstudio.nyx.pet.PetVisibility
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Home's focus area: the running session, else Start (hidden when focus is switched off, D-120). */
data class HomeFocusState(val running: FocusStatus?, val enabled: Boolean)

class HomeViewModel(
    summaryRepository: SummaryRepository,
    private val petVisibility: PetVisibility,
    private val focusRepository: FocusRepository,
) : ViewModel() {
    /** `null` while loading. */
    val summary: StateFlow<TodaySummary?> = summaryRepository.observeToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /** While true, Home offers to show the pet again (US-29). */
    val petHidden: StateFlow<Boolean> = petVisibility.shown().map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = false)

    val focus: StateFlow<HomeFocusState> = combine(focusRepository.observeStatus(), focusRepository.observeEnabled(), ::HomeFocusState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = HomeFocusState(running = null, enabled = false))

    /** Writes storage; the pet service follows it (D-44). */
    fun showPet(): Job = viewModelScope.launch { petVisibility.show() }

    /** Done or End on the session card; storage again, the engine notices (D-109). */
    fun finishFocus(done: Boolean): Job = viewModelScope.launch { focusRepository.finish(done) }
}
