package com.companionstudio.nyx.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.NyxConfig
import com.companionstudio.nyx.tracking.AppCategoryRepository
import com.companionstudio.nyx.tracking.InstalledApp
import com.companionstudio.nyx.tracking.suggestCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppRow(val packageName: String, val label: String, val category: Category)

/** The user's choice wins over the suggestion; [query] matches anywhere in the label, ignoring case. */
fun appRows(apps: List<InstalledApp>, choices: Map<String, Category>, document: NyxConfig, query: String): List<AppRow> {
    val search = query.trim()
    return apps
        .filter { search.isEmpty() || it.label.contains(search, ignoreCase = true) }
        .map { AppRow(it.packageName, it.label, choices[it.packageName] ?: suggestCategory(it.packageName, it.androidCategory, document)) }
}

class AppCategoriesViewModel(
    loadApps: suspend () -> List<InstalledApp>,
    config: Flow<EffectiveConfig>,
    private val repository: AppCategoryRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")

    /** Kept apart from [rows] so the search field updates on every keystroke without waiting for the list. */
    val query: StateFlow<String> = _query.asStateFlow()

    /** `null` while the app list loads. */
    val rows: StateFlow<List<AppRow>?> = combine(flow { emit(loadApps()) }, repository.observeChoices(), config, _query) {
        apps, choices, effective, query -> appRows(apps, choices, effective.document, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun search(text: String) {
        _query.value = text
    }

    /** Stored straight away: tracking uses the choice from this moment on (US-04). */
    fun choose(packageName: String, category: Category) {
        viewModelScope.launch { repository.choose(packageName, category) }
    }
}
