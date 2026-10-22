package com.companionstudio.nyx.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigUserValues
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.Setting
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.ui.SettingSlider
import com.companionstudio.nyx.ui.labelRes
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.flow.Flow

@Composable
fun LimitsRoute(config: Flow<EffectiveConfig>, userValues: ConfigUserValues, onNext: () -> Unit) {
    val viewModel = viewModel { LimitsViewModel(config, userValues) }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    LimitsScreen(rows, onSet = { row, minutes -> viewModel.set(row, minutes) }, onNext = onNext)
}

/** S-06. [rows] is `null` while loading. Nag timing, breaks and the reset hour are in Settings (S-11). */
@Composable
fun LimitsScreen(rows: List<LimitRow>?, onSet: (LimitRow, Int) -> Unit, onNext: () -> Unit) {
    SetupStep(stringResource(R.string.limits_title)) {
        Text(stringResource(R.string.limits_intro))
        if (rows == null) {
            CircularProgressIndicator()
            return@SetupStep
        }
        val (goals, limits) = rows.partition { it.isGoal }
        limits.forEach { row -> LimitCard(row, onSet = { onSet(row, it) }) }
        if (goals.isNotEmpty()) {
            Text(stringResource(R.string.goals_heading), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.goals_intro))
        }
        goals.forEach { row -> LimitCard(row, onSet = { onSet(row, it) }) }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_continue))
        }
    }
}

@Composable
private fun LimitCard(row: LimitRow, onSet: (Int) -> Unit) {
    val name = limitName(row.name)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (row.name is LimitName.Stretch) {
                Text(stringResource(R.string.limits_stretch_covers, row.name.categories.map { stringResource(it.labelRes) }.joinToString()))
            }
            SettingSlider(
                name,
                row.setting,
                row.value,
                format = { if (row.name == LimitName.FocusSessions) sessionsText(it) else minutesText(it) },
                onSet = onSet,
            )
        }
    }
}

@Composable
private fun limitName(name: LimitName): String = when (name) {
    is LimitName.CategoryDaily -> stringResource(R.string.limits_category_daily, stringResource(name.category.labelRes))
    LimitName.TotalDaily -> stringResource(R.string.limits_total_daily)
    is LimitName.Stretch -> stringResource(R.string.limits_stretch)
    LimitName.FocusSessions -> stringResource(R.string.goals_focus_sessions)
    LimitName.ProductiveMinutes -> stringResource(R.string.goals_productive)
}

@Composable
private fun minutesText(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.limits_off) else formatDuration(minutes * 60L)

@Composable
private fun sessionsText(sessions: Int): String =
    if (sessions == 0) stringResource(R.string.limits_off) else pluralStringResource(R.plurals.goals_sessions, sessions, sessions)

@Preview(showBackground = true)
@Composable
private fun LimitsScreenPreview() {
    NyxTheme {
        LimitsScreen(
            rows = listOf(
                LimitRow("limits.categories.social.dailyMinutes", LimitName.CategoryDaily(Category.SOCIAL), Setting(60, 0, 240, true), 60),
                LimitRow("limits.totalDailyMinutes", LimitName.TotalDaily, Setting(0, 0, 960, false), 0),
                LimitRow("goals.focusSessionsPerDay", LimitName.FocusSessions, Setting(0, 0, 10, true), 2),
            ),
            onSet = { _, _ -> },
            onNext = {},
        )
    }
}
