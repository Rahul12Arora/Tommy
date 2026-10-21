package com.companionstudio.nyx.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R
import com.companionstudio.nyx.ui.labelRes

/** Today's totals and goals. Home (S-08) and the pet's summary panel (O-03) both show this, so they always agree. */
@Composable
fun SummaryNumbers(summary: TodaySummary, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.home_total, formatDuration(summary.totalSeconds)),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(stringResource(R.string.home_productive, formatDuration(summary.productiveSeconds)))
        Text(stringResource(R.string.home_doomscrolling, formatDuration(summary.doomscrollSeconds)))
        Text(stringResource(R.string.home_goals), style = MaterialTheme.typography.titleMedium)
        summary.goals.forEach { GoalRow(it) }
    }
}

/** One limit goal; also the rows of the ring's Limits list (§14.7). */
@Composable
fun GoalRow(goal: GoalProgress) {
    val used = formatDuration(goal.usedSeconds)
    val target = formatDuration(goal.targetSeconds)
    val name = if (goal.category == null) stringResource(R.string.home_goal_total) else stringResource(goal.category.labelRes)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.home_goal_label, name, target))
        // "over" is spelled out so the state never relies on colour alone (ARCHITECTURE §9)
        Text(
            if (goal.isOver) stringResource(R.string.home_goal_over, used, target)
            else stringResource(R.string.home_goal_progress, used, target),
            style = MaterialTheme.typography.bodySmall,
        )
        LinearProgressIndicator(
            progress = { (goal.usedSeconds.toFloat() / goal.targetSeconds).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
