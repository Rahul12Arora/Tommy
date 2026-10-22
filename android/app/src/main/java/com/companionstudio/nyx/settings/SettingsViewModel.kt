package com.companionstudio.nyx.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companionstudio.nyx.config.ConfigUserValues
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.Setting
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.rules.BreakMode
import com.companionstudio.nyx.rules.ReactionPrefs
import com.companionstudio.nyx.rules.ReactionSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A number S-11 edits. [value] is the effective one, inside the admin's range. */
data class SettingRow(val path: String, val setting: Setting, val value: Int)

/**
 * S-11's state (§14.7). A row is `null` when its feature is switched off for everyone: the break rows without
 * `features.breaks`, the idle switch without `features.idleLines`. [prefs] holds the user's stored choices; the mode
 * shown is the stored one even when [obstructiveAllowed] is `false`, but the screen then disables it.
 */
data class SettingsState(
    val nagRepeat: SettingRow,
    val snooze: SettingRow,
    val breakAfter: SettingRow?,
    val resetHour: SettingRow,
    val prefs: ReactionPrefs,
    val obstructiveAllowed: Boolean,
    val buttonDelaySeconds: Int,
    val idleAvailable: Boolean,
)

fun settingsState(config: EffectiveConfig, prefs: ReactionPrefs): SettingsState {
    fun row(path: String) = SettingRow(path, config.settings.getValue(path), checkNotNull(config.valueOf(path)))
    val document = config.document
    return SettingsState(
        nagRepeat = row(SettingPaths.NAG_REPEAT_MINUTES),
        snooze = row(SettingPaths.SNOOZE_MINUTES),
        breakAfter = if (document.features.breaks) row(SettingPaths.BREAK_AFTER_MINUTES) else null,
        resetHour = row(SettingPaths.RESET_HOUR),
        prefs = prefs,
        obstructiveAllowed = document.breaks.obstructive.allowed,
        buttonDelaySeconds = document.breaks.obstructive.buttonDelaySeconds,
        idleAvailable = document.features.idleLines,
    )
}

class SettingsViewModel(
    config: Flow<EffectiveConfig>,
    private val userValues: ConfigUserValues,
    private val reactionSettings: ReactionSettings,
) : ViewModel() {

    /** `null` while loading. */
    val state: StateFlow<SettingsState?> = combine(config, reactionSettings.prefs, ::settingsState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /** Clamped into the admin's range when stored (D-61). The returned jobs let tests wait for the write. */
    fun set(row: SettingRow, value: Int): Job = viewModelScope.launch { userValues.set(row.path, row.setting, value) }

    fun setMode(mode: BreakMode): Job = viewModelScope.launch { reactionSettings.setMode(mode) }

    fun setIdleEnabled(enabled: Boolean): Job = viewModelScope.launch { reactionSettings.setIdleEnabled(enabled) }
}
