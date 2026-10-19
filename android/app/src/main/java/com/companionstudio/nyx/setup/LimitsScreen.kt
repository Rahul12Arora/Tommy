package com.companionstudio.nyx.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.companionstudio.nyx.ui.labelRes
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

@Composable
fun LimitsRoute(config: Flow<EffectiveConfig>, userValues: ConfigUserValues, onNext: () -> Unit) {
    val viewModel = viewModel { LimitsViewModel(config, userValues) }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    LimitsScreen(rows, onSet = { row, minutes -> viewModel.set(row, minutes) }, onNext = onNext)
}

/** S-06. [rows] is `null` while loading. Nag timing, breaks and the reset hour are in Settings (M7). */
@Composable
fun LimitsScreen(rows: List<LimitRow>?, onSet: (LimitRow, Int) -> Unit, onNext: () -> Unit) {
    SetupStep(stringResource(R.string.limits_title)) {
        Text(stringResource(R.string.limits_intro))
        if (rows == null) {
            CircularProgressIndicator()
            return@SetupStep
        }
        rows.forEach { row -> LimitCard(row, onSet = { onSet(row, it) }) }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_continue))
        }
    }
}

@Composable
private fun LimitCard(row: LimitRow, onSet: (Int) -> Unit) {
    val name = limitName(row.name)
    // Follows the finger while dragging; stored only on release. Starts again from the stored value when it changes
    var dragged by remember(row.value) { mutableFloatStateOf(row.value.toFloat()) }
    val shown = minutesText(dragged.roundToInt())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (row.name is LimitName.Stretch) {
                Text(stringResource(R.string.limits_stretch_covers, row.name.categories.map { stringResource(it.labelRes) }.joinToString()))
            }
            Text(shown, style = MaterialTheme.typography.headlineSmall)
            if (row.editable) {
                val step = sliderStepMinutes(row.setting)
                Slider(
                    value = dragged,
                    onValueChange = { dragged = it },
                    onValueChangeFinished = { onSet(dragged.roundToInt()) },
                    valueRange = row.setting.min.toFloat()..row.setting.max.toFloat(),
                    steps = (row.setting.max - row.setting.min) / step - 1,
                    // Up to ~190 steps per slider: their tick marks would merge into a dotted line, so they're hidden
                    colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                    modifier = Modifier.semantics {
                        contentDescription = name
                        stateDescription = shown
                    },
                )
            } else {
                // Locked by the admin: shown, not editable (US-05)
                Text(stringResource(R.string.limits_locked), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun limitName(name: LimitName): String = when (name) {
    is LimitName.CategoryDaily -> stringResource(R.string.limits_category_daily, stringResource(name.category.labelRes))
    LimitName.TotalDaily -> stringResource(R.string.limits_total_daily)
    is LimitName.Stretch -> stringResource(R.string.limits_stretch)
}

@Composable
private fun minutesText(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.limits_off) else formatDuration(minutes * 60L)

@Preview(showBackground = true)
@Composable
private fun LimitsScreenPreview() {
    NyxTheme {
        LimitsScreen(
            rows = listOf(
                LimitRow("limits.categories.social.dailyMinutes", LimitName.CategoryDaily(Category.SOCIAL), Setting(60, 0, 240, true), 60),
                LimitRow("limits.totalDailyMinutes", LimitName.TotalDaily, Setting(0, 0, 960, false), 0),
            ),
            onSet = { _, _ -> },
            onNext = {},
        )
    }
}
