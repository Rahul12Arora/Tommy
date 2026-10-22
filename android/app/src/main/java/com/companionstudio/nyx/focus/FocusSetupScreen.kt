package com.companionstudio.nyx.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.tracking.InstalledApps
import com.companionstudio.nyx.ui.SettingSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Everything the user can change on S-09. */
class FocusSetupActions(
    val onGoal: (FocusGoal) -> Unit,
    val onMinutes: (Int) -> Unit,
    val onToggleApp: (String) -> Unit,
    val onPetMode: (PetMode) -> Unit,
    val onCheckinMinutes: (Int) -> Unit,
    val onSearch: (String) -> Unit,
    val onStart: () -> Unit,
)

/** [fromApp]: the app in front when S-09 was opened from the ring (D-117). [onStarted] runs once the session is written. */
@Composable
fun FocusSetupRoute(installedApps: InstalledApps, config: Flow<EffectiveConfig>, repository: FocusRepository, fromApp: String?, onStarted: () -> Unit) {
    val viewModel = viewModel { FocusSetupViewModel(installedApps::list, config, repository, fromApp) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val iconPx = with(LocalDensity.current) { APP_ICON_SIZE.roundToPx() }
    val session = running
    if (session != null) {
        // A stale ring tap or back-stack entry can still land here while a session runs
        Scaffold { innerPadding ->
            FocusSessionCard(
                session,
                onDone = { viewModel.finish(done = true) },
                onEnd = { viewModel.finish(done = false) },
                modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxWidth(),
            )
        }
        return
    }
    FocusSetupScreen(
        state = state,
        query = query,
        actions = FocusSetupActions(
            onGoal = viewModel::setGoal,
            onMinutes = viewModel::setMinutes,
            onToggleApp = viewModel::toggleApp,
            onPetMode = viewModel::setPetMode,
            onCheckinMinutes = viewModel::setCheckinMinutes,
            onSearch = viewModel::search,
            onStart = { viewModel.start(onStarted) },
        ),
        // Icons load per visible row, off the main thread, as on S-05
        loadIcon = { packageName ->
            withContext(Dispatchers.IO) { installedApps.icon(packageName)?.toBitmap(iconPx, iconPx)?.asImageBitmap() }
        },
    )
}

/** S-09 (US-12). [state] is `null` while the starting choices load. Start stays in reach below the scrolling form. */
@Composable
fun FocusSetupScreen(state: FocusSetupState?, query: String, actions: FocusSetupActions, loadIcon: suspend (String) -> ImageBitmap?) {
    Scaffold { innerPadding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val choices = state.choices
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Heading(stringResource(R.string.focus_setup_title), large = true) }
                item { Heading(stringResource(R.string.focus_goal)) }
                item {
                    Choices(
                        options = FocusGoal.entries,
                        selected = choices.goal,
                        label = { goalName(it) },
                        hint = { stringResource(goalHint(it)) },
                        onSelect = actions.onGoal,
                    )
                }
                if (choices.goal != FocusGoal.UNTIL_DONE) {
                    item {
                        Column {
                            val name = stringResource(R.string.focus_length)
                            Heading(name)
                            SettingSlider(name, state.duration, choices.minutes, format = { formatDuration(it * 60L) }, onSet = actions.onMinutes)
                        }
                    }
                }
                item { Heading(stringResource(R.string.focus_pet_mode)) }
                item {
                    Choices(
                        options = PetMode.entries,
                        selected = choices.petMode,
                        label = { stringResource(modeName(it)) },
                        hint = { stringResource(modeHint(it)) },
                        onSelect = actions.onPetMode,
                    )
                }
                if (choices.petMode == PetMode.CHECK_IN) {
                    item {
                        Column {
                            val name = stringResource(R.string.focus_checkin_every)
                            Heading(name)
                            SettingSlider(name, state.checkin, choices.checkinMinutes, format = { formatDuration(it * 60L) }, onSet = actions.onCheckinMinutes)
                        }
                    }
                }
                item {
                    Column {
                        Heading(stringResource(R.string.focus_apps))
                        Text(stringResource(R.string.focus_apps_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                allowedApps(state.apps, query, actions.onSearch, actions.onToggleApp, loadIcon)
            }
            Button(onClick = actions.onStart, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.focus_start))
            }
        }
    }
}

@Composable
private fun Heading(text: String, large: Boolean = false) {
    val style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium
    Text(text, style = style, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/** A radio group: one row per option, its label and a one-line hint. */
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, hint: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    Text(hint(option), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun goalHint(goal: FocusGoal) = when (goal) {
    FocusGoal.FIXED_TIME -> R.string.focus_goal_fixed_time_hint
    FocusGoal.UNTIL_DONE -> R.string.focus_goal_until_done_hint
    FocusGoal.NO_SWITCHING -> R.string.focus_goal_no_switching_hint
}

private fun modeName(mode: PetMode) = when (mode) {
    PetMode.QUIET -> R.string.focus_mode_quiet
    PetMode.REWARD_AT_END -> R.string.focus_mode_reward
    PetMode.CHECK_IN -> R.string.focus_mode_checkin
}

private fun modeHint(mode: PetMode) = when (mode) {
    PetMode.QUIET -> R.string.focus_mode_quiet_hint
    PetMode.REWARD_AT_END -> R.string.focus_mode_reward_hint
    PetMode.CHECK_IN -> R.string.focus_mode_checkin_hint
}
