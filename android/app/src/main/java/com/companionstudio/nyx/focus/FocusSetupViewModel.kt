package com.companionstudio.nyx.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.Setting
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.tracking.InstalledApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user has picked on S-09 so far. */
data class FocusChoices(
    val goal: FocusGoal,
    val minutes: Int,
    val apps: Set<String>,
    val petMode: PetMode,
    val checkinMinutes: Int,
)

/** One row of the allowed-apps list. */
data class AppChoice(val packageName: String, val label: String, val selected: Boolean)

/** S-09 as drawn. [apps] is `null` while the app list loads; [canStart]: at least one installed app is picked. */
data class FocusSetupState(
    val choices: FocusChoices,
    val duration: Setting,
    val checkin: Setting,
    val apps: List<AppChoice>?,
    val canStart: Boolean,
)

/**
 * S-09's starting choices (D-117): the last session's, clamped into today's ranges, else the config's. A locked slider
 * always shows the admin's value. Opened from the ring, the app the user was in is the one selected app.
 */
fun initialChoices(last: FocusSession?, config: EffectiveConfig, fromApp: String?): FocusChoices {
    val focus = config.document.focus
    fun remembered(lastValue: Int?, setting: Setting, path: String): Int {
        val value = checkNotNull(config.valueOf(path))
        return if (setting.userEditable && lastValue != null) lastValue.coerceIn(setting.min, setting.max) else value
    }
    return FocusChoices(
        goal = last?.goal ?: FocusGoal.FIXED_TIME,
        minutes = remembered(last?.goalMinutes, focus.durationMinutes, SettingPaths.FOCUS_DURATION_MINUTES),
        apps = fromApp?.let(::setOf) ?: last?.allowedApps?.toSet().orEmpty(),
        petMode = last?.petMode ?: PetMode.CHECK_IN,
        checkinMinutes = remembered(last?.checkinMinutes, focus.checkinMinutes, SettingPaths.FOCUS_CHECKIN_MINUTES),
    )
}

/** Picked apps first, then the rest; [query] matches anywhere in the label, ignoring case. */
fun appChoices(apps: List<InstalledApp>, selected: Set<String>, query: String): List<AppChoice> {
    val search = query.trim()
    return apps
        .filter { search.isEmpty() || it.label.contains(search, ignoreCase = true) }
        .map { AppChoice(it.packageName, it.label, it.packageName in selected) }
        .sortedByDescending { it.selected }
}

class FocusSetupViewModel(
    loadApps: suspend () -> List<InstalledApp>,
    private val config: Flow<EffectiveConfig>,
    private val repository: FocusRepository,
    fromApp: String?,
) : ViewModel() {
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val choices = MutableStateFlow<FocusChoices?>(null)
    private val _query = MutableStateFlow("")

    /** Kept apart from [state] so the search field updates on every keystroke. */
    val query: StateFlow<String> = _query.asStateFlow()

    /** The running session, if any: S-09 shows it instead of the form. */
    val running: StateFlow<FocusStatus?> = repository.observeStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /** `null` until the starting choices are known. */
    val state: StateFlow<FocusSetupState?> = combine(choices, config, installed, _query) { picked, effective, apps, query ->
        picked ?: return@combine null
        val focus = effective.document.focus
        val installedNames = apps?.map { it.packageName }?.toSet()
        FocusSetupState(
            choices = picked,
            duration = focus.durationMinutes,
            checkin = focus.checkinMinutes,
            apps = apps?.let { appChoices(it, picked.apps, query) },
            canStart = installedNames != null && picked.apps.any { it in installedNames },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    init {
        viewModelScope.launch { choices.value = initialChoices(repository.latest(), config.first(), fromApp) }
        viewModelScope.launch { installed.value = loadApps() }
    }

    fun setGoal(goal: FocusGoal) = change { it.copy(goal = goal) }

    fun setMinutes(minutes: Int) = change { it.copy(minutes = minutes) }

    fun toggleApp(packageName: String) = change { it.copy(apps = if (packageName in it.apps) it.apps - packageName else it.apps + packageName) }

    fun setPetMode(mode: PetMode) = change { it.copy(petMode = mode) }

    fun setCheckinMinutes(minutes: Int) = change { it.copy(checkinMinutes = minutes) }

    fun search(text: String) {
        _query.value = text
    }

    /** Writes the session; the service follows (D-44). [onStarted] runs only if it was written. */
    fun start(onStarted: () -> Unit): Job = viewModelScope.launch {
        val picked = choices.value ?: return@launch
        // A remembered app that was uninstalled since isn't allowed any more
        val installedNames = installed.value?.map { it.packageName }?.toSet() ?: return@launch
        val apps = picked.apps.filter { it in installedNames }
        val request = FocusRequest(picked.goal, picked.minutes, apps, picked.petMode, picked.checkinMinutes)
        if (repository.start(request)) onStarted()
    }

    fun finish(done: Boolean): Job = viewModelScope.launch { repository.finish(done) }

    private fun change(edit: (FocusChoices) -> FocusChoices) = choices.update { it?.let(edit) }
}
