package com.companionstudio.nyx.pet

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R
import com.companionstudio.nyx.home.GoalProgress
import com.companionstudio.nyx.home.GoalRow
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.rules.LimitKeys
import com.companionstudio.nyx.rules.NagPhase
import com.companionstudio.nyx.rules.NagState
import com.companionstudio.nyx.rules.RingStatus
import java.util.Date

/** The Limits slot's list (D-90): Home's goal rows, the stretch, and each limit's nag state. */
@Composable
internal fun LimitsList(goals: List<GoalProgress>, status: RingStatus, side: Side, geometry: RingGeometry, onBack: () -> Unit) {
    val besidePet = PET_SIZE.value + PANEL_GAP.value
    val width = (geometry.width - besidePet).coerceAtMost(280f)
    val left = if (side == Side.LEFT) besidePet else geometry.width - besidePet - width
    Box(contentAlignment = Alignment.Center, modifier = Modifier.offset(left.dp, 0.dp).size(width.dp, geometry.height.dp)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier.heightIn(max = geometry.height.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.ring_limits_title), style = MaterialTheme.typography.titleMedium)
                if (goals.isEmpty() && status.stretchLimitMinutes == null) Text(stringResource(R.string.ring_limits_none))
                goals.forEach { goal ->
                    GoalRow(goal)
                    NagNote(status.nags[goal.category?.let(LimitKeys::category) ?: LimitKeys.TOTAL])
                }
                status.stretchLimitMinutes?.let { limit ->
                    val over = status.stretchMs >= limit * 60_000L
                    val used = formatDuration(status.stretchMs / 1000)
                    val target = formatDuration(limit * 60L)
                    Text(stringResource(if (over) R.string.ring_stretch_over else R.string.ring_stretch, used, target))
                    NagNote(status.nags[LimitKeys.STRETCH])
                }
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.ring_back)) }
            }
        }
    }
}

@Composable
private fun NagNote(state: NagState?) {
    val until = state?.snoozedUntil?.takeIf { state.phase == NagPhase.SNOOZED } ?: return
    val time = DateFormat.getTimeFormat(LocalContext.current).format(Date.from(until))
    Text(stringResource(R.string.ring_snoozed_until, time), style = MaterialTheme.typography.bodySmall)
}
