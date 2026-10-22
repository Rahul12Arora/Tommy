package com.companionstudio.nyx.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R
import com.companionstudio.nyx.ui.theme.NyxTheme

/**
 * The running session on Home, S-09 and the ring (D-118): goal, time, switches, and Done (until done only, once
 * allowed, D-113) and End. [extra] goes below the buttons, e.g. the ring's Back.
 */
@Composable
fun FocusSessionContent(status: FocusStatus, onDone: () -> Unit, onEnd: () -> Unit, extra: @Composable () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.focus_session_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(goalName(status.goal), style = MaterialTheme.typography.labelLarge)
        Text(focusSummaryText(status))
        if (status.goal == FocusGoal.UNTIL_DONE && !status.canDone) {
            Text(stringResource(R.string.focus_done_after, status.minDoneMinutes), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status.goal == FocusGoal.UNTIL_DONE) {
                Button(onClick = onDone, enabled = status.canDone) { Text(stringResource(R.string.focus_done)) }
            }
            OutlinedButton(onClick = onEnd) { Text(stringResource(R.string.focus_end)) }
        }
        extra()
    }
}

/** [FocusSessionContent] on a card, for Home and S-09. */
@Composable
fun FocusSessionCard(status: FocusStatus, onDone: () -> Unit, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) { FocusSessionContent(status, onDone, onEnd) }
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusSessionCardPreview() {
    NyxTheme {
        FocusSessionCard(
            FocusStatus(FocusGoal.UNTIL_DONE, null, focusMs = 3 * 60_000L, FocusPhase.DRIFTING, switches = 2, canDone = false, minDoneMinutes = 5),
            onDone = {},
            onEnd = {},
        )
    }
}
