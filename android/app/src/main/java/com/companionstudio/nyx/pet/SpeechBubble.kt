package com.companionstudio.nyx.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R
import com.companionstudio.nyx.ui.theme.NyxTheme

/**
 * O-02: one line in the pet's personality, shown beside the pet in its window (D-84). [onSnooze] is `null` except
 * on nags that can still be snoozed (US-10, M7).
 */
@Composable
fun SpeechBubble(line: String, onSnooze: (() -> Unit)?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        // TalkBack reads a bubble when it appears, without moving focus away from what the user is doing
        modifier = modifier.widthIn(max = 280.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(line, modifier = Modifier.padding(end = 8.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onSnooze?.let { TextButton(onClick = it) { Text(stringResource(R.string.bubble_snooze)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.bubble_dismiss)) }
            }
        }
    }
}

@Preview
@Composable
private fun SpeechBubblePreview() {
    NyxTheme {
        SpeechBubble("That's 1 h of Social & scrolling today. Stretch break?", onSnooze = {}, onDismiss = {})
    }
}
