package com.companionstudio.nyx.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.delay

/**
 * O-04, the break screen (CR-10, D-88): the pet, how long the screen has been on, a `break_due` line, and two buttons
 * that only work after [buttonDelaySeconds] real seconds. [onFiveMore] is `null` once this sitting's snoozes are used up.
 */
@Composable
fun BreakScreen(
    face: String,
    continuousMs: Long,
    line: String,
    buttonDelaySeconds: Int,
    onTakeBreak: () -> Unit,
    onFiveMore: (() -> Unit)?,
) {
    var secondsLeft by remember { mutableIntStateOf(buttonDelaySeconds) }
    // Reading time, so real seconds even under the debug fast clock (D-67)
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }
    val ready = secondsLeft == 0
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)).padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            // Sized in dp, like the pet's face elsewhere
            val faceSize = with(LocalDensity.current) { 96.dp.toSp() }
            Text(face, fontSize = faceSize, modifier = Modifier.clearAndSetSemantics {})
            // The scrim is dark in both themes
            val white = Color.White
            Text(
                stringResource(R.string.break_screen_heading, formatDuration(continuousMs / 1000)),
                style = MaterialTheme.typography.headlineSmall,
                color = white,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Text(line, color = white, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            val wait = if (ready) null else stringResource(R.string.break_screen_wait, secondsLeft)
            Button(onClick = onTakeBreak, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                Text(wait ?: stringResource(R.string.break_screen_take_break))
            }
            onFiveMore?.let {
                OutlinedButton(onClick = it, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text(wait ?: stringResource(R.string.break_screen_five_more), color = white)
                }
            }
        }
    }
}

@Preview
@Composable
private fun BreakScreenPreview() {
    NyxTheme {
        BreakScreen("😿", 92 * 60_000L, "Break time! Put the phone down for 5 minutes.", 0, onTakeBreak = {}, onFiveMore = {})
    }
}
