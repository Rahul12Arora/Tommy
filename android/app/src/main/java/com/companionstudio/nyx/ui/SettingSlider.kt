package com.companionstudio.nyx.ui

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
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Setting
import kotlin.math.roundToInt

/** Steps of 5 on ranges longer than 60 that divide evenly by 5, otherwise 1, so every step lands inside the range. */
fun sliderStepMinutes(setting: Setting): Int {
    val span = setting.max - setting.min
    return if (span > 60 && span % 5 == 0) 5 else 1
}

/**
 * A setting's value and its slider within the admin's range (S-06, S-11). A locked setting shows its value and
 * "Set by Nyx" instead (US-05). The value follows the finger while dragging and is stored only on release (D-61).
 */
@Composable
fun SettingSlider(name: String, setting: Setting, value: Int, format: @Composable (Int) -> String, onSet: (Int) -> Unit) {
    // Starts again from the stored value when it changes
    var dragged by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = format(dragged.roundToInt())
    Text(shown, style = MaterialTheme.typography.headlineSmall)
    if (!setting.userEditable) {
        Text(stringResource(R.string.limits_locked), style = MaterialTheme.typography.labelLarge)
        return
    }
    val step = sliderStepMinutes(setting)
    Slider(
        value = dragged,
        onValueChange = { dragged = it },
        onValueChangeFinished = { onSet(dragged.roundToInt()) },
        valueRange = setting.min.toFloat()..setting.max.toFloat(),
        steps = ((setting.max - setting.min) / step - 1).coerceAtLeast(0),
        // Up to ~190 steps per slider: their tick marks would merge into a dotted line, so they're hidden
        colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        modifier = Modifier.semantics {
            contentDescription = name
            stateDescription = shown
        },
    )
}
