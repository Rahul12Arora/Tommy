package com.companionstudio.nyx.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.ConfigUserValues
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.Setting
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.rules.BreakMode
import com.companionstudio.nyx.rules.ReactionPrefs
import com.companionstudio.nyx.rules.ReactionSettings
import com.companionstudio.nyx.setup.SetupStep
import com.companionstudio.nyx.ui.SettingSlider
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.flow.Flow

/** The screens S-11 opens; each comes back here when done (§14.7). */
class SettingsLinks(
    val onPet: () -> Unit,
    val onCategories: () -> Unit,
    val onLimits: () -> Unit,
    val onStats: () -> Unit,
    val onPermissions: () -> Unit,
)

/** What S-11 can change, as callbacks. */
class SettingsActions(
    val onSet: (SettingRow, Int) -> Unit,
    val onMode: (BreakMode) -> Unit,
    val onIdle: (Boolean) -> Unit,
)

@Composable
fun SettingsRoute(
    config: Flow<EffectiveConfig>,
    userValues: ConfigUserValues,
    reactionSettings: ReactionSettings,
    links: SettingsLinks,
) {
    val viewModel = viewModel { SettingsViewModel(config, userValues, reactionSettings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state, links, SettingsActions(onSet = { row, value -> viewModel.set(row, value) }, viewModel::setMode, viewModel::setIdleEnabled))
}

/** S-11. [state] is `null` while loading. Locked settings show their value read-only (D-16). */
@Composable
fun SettingsScreen(state: SettingsState?, links: SettingsLinks, actions: SettingsActions) {
    SetupStep(stringResource(R.string.settings_title)) {
        Link(stringResource(R.string.settings_pet), links.onPet)
        Link(stringResource(R.string.settings_categories), links.onCategories)
        Link(stringResource(R.string.settings_limits), links.onLimits)
        if (state == null) {
            CircularProgressIndicator()
            return@SetupStep
        }

        Section(stringResource(R.string.settings_nags))
        SliderCard(stringResource(R.string.settings_nag_repeat), state.nagRepeat, actions)
        SliderCard(stringResource(R.string.settings_snooze), state.snooze, actions)

        state.breakAfter?.let { breakAfter ->
            Section(stringResource(R.string.settings_breaks))
            SliderCard(stringResource(R.string.settings_break_after), breakAfter, actions)
            BreakModeCard(state, actions.onMode)
        }

        if (state.idleAvailable) {
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(state.prefs.idleEnabled, role = Role.Switch, onValueChange = actions.onIdle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_idle))
                    Text(stringResource(R.string.settings_idle_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.prefs.idleEnabled, onCheckedChange = null)
            }
        }

        Section(stringResource(R.string.settings_day))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val name = stringResource(R.string.settings_reset_hour)
                Text(name, style = MaterialTheme.typography.titleMedium)
                SettingSlider(name, state.resetHour.setting, state.resetHour.value, format = { it.toString().padStart(2, '0') + ":00" }) {
                    actions.onSet(state.resetHour, it)
                }
            }
        }

        HorizontalDivider()
        Link(stringResource(R.string.settings_stats), links.onStats)
        Link(stringResource(R.string.settings_permissions), links.onPermissions)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun SliderCard(name: String, row: SettingRow, actions: SettingsActions) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            SettingSlider(name, row.setting, row.value, format = { formatDuration(it * 60L) }) { actions.onSet(row, it) }
        }
    }
}

/** Gentle (US-11) or the obstructive break screen (CR-10, D-88), which the admin can turn off for everyone. */
@Composable
private fun BreakModeCard(state: SettingsState, onMode: (BreakMode) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_break_mode), style = MaterialTheme.typography.titleMedium)
            ModeOption(
                stringResource(R.string.settings_mode_gentle),
                stringResource(R.string.settings_mode_gentle_hint),
                selected = state.prefs.mode == BreakMode.GENTLE || !state.obstructiveAllowed,
                enabled = true,
            ) { onMode(BreakMode.GENTLE) }
            ModeOption(
                stringResource(R.string.settings_mode_obstructive),
                if (state.obstructiveAllowed) {
                    stringResource(R.string.settings_mode_obstructive_hint, state.buttonDelaySeconds)
                } else {
                    stringResource(R.string.settings_mode_obstructive_off)
                },
                selected = state.prefs.mode == BreakMode.OBSTRUCTIVE && state.obstructiveAllowed,
                enabled = state.obstructiveAllowed,
            ) { onMode(BreakMode.OBSTRUCTIVE) }
        }
    }
}

@Composable
private fun ModeOption(label: String, hint: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    val minutes = Setting(10, 5, 30, true)
    NyxTheme {
        SettingsScreen(
            SettingsState(
                nagRepeat = SettingRow("nags.repeatMinutes", minutes, 10),
                snooze = SettingRow("nags.snoozeMinutes", minutes, 10),
                breakAfter = SettingRow("breaks.afterMinutes", Setting(45, 20, 120, true), 45),
                resetHour = SettingRow("day.resetHour", Setting(0, 0, 6, true), 4),
                prefs = ReactionPrefs(),
                obstructiveAllowed = true,
                buttonDelaySeconds = 10,
                idleAvailable = true,
            ),
            SettingsLinks({}, {}, {}, {}, {}),
            SettingsActions({ _, _ -> }, {}, {}),
        )
    }
}
