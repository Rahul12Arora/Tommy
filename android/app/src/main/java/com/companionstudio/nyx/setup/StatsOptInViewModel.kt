package com.companionstudio.nyx.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companionstudio.nyx.stats.StatsSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatsOptInViewModel(private val statsSettings: StatsSettings, private val setupSettings: SetupSettings) : ViewModel() {

    /** `null` while loading. */
    val optIn: StateFlow<Boolean?> = statsSettings.optIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /** The returned job lets a caller wait for the write; the screen doesn't need to. */
    fun setOptIn(on: Boolean): Job = viewModelScope.launch { statsSettings.setOptIn(on) }

    /** The last step of setup: from now on the launcher opens Home (§14.3). */
    suspend fun finishSetup() {
        setupSettings.markCompleted()
    }
}
