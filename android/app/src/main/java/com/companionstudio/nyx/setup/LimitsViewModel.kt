package com.companionstudio.nyx.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigUserValues
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.Setting
import com.companionstudio.nyx.config.SettingPaths
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a limit row is about, so the screen can name it. */
sealed interface LimitName {
    data class CategoryDaily(val category: Category) : LimitName
    data object TotalDaily : LimitName
    data class Stretch(val categories: List<Category>) : LimitName

    // Positive goals share the screen and the slider (D-147)
    data object FocusSessions : LimitName
    data object ProductiveMinutes : LimitName
}

/** One limit or goal on S-06. [value] is the effective value in minutes, or sessions; 0 = off except for the stretch. */
data class LimitRow(val path: String, val name: LimitName, val setting: Setting, val value: Int) {
    val editable: Boolean get() = setting.userEditable
    val isGoal: Boolean get() = name == LimitName.FocusSessions || name == LimitName.ProductiveMinutes
}

/** The settings in display order: category daily limits, the total, the stretch (§14.3), then positive goals (§14.13). */
fun limitRows(effective: EffectiveConfig): List<LimitRow> = buildList {
    fun add(path: String, name: LimitName) {
        val setting = effective.settings[path] ?: return // a category without a limit can't get one (A-14)
        add(LimitRow(path, name, setting, checkNotNull(effective.valueOf(path))))
    }
    for (category in Category.entries) add(SettingPaths.categoryDailyMinutes(category), LimitName.CategoryDaily(category))
    add(SettingPaths.TOTAL_DAILY_MINUTES, LimitName.TotalDaily)
    add(SettingPaths.STRETCH_MINUTES, LimitName.Stretch(effective.document.limits.stretch.categories))
    add(SettingPaths.GOAL_FOCUS_SESSIONS, LimitName.FocusSessions)
    add(SettingPaths.GOAL_PRODUCTIVE_MINUTES, LimitName.ProductiveMinutes)
}

class LimitsViewModel(config: Flow<EffectiveConfig>, private val userValues: ConfigUserValues) : ViewModel() {

    /** `null` while loading. */
    val rows: StateFlow<List<LimitRow>?> = config.map(::limitRows)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /**
     * Stored when the slider is released (D-61); locked rows have no slider, so this never sees one.
     * The returned job lets a caller wait for the write; the screen doesn't need to.
     */
    fun set(row: LimitRow, minutes: Int): Job = viewModelScope.launch { userValues.set(row.path, row.setting, minutes) }
}
