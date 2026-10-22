package com.companionstudio.nyx.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.rules.Goal
import com.companionstudio.nyx.rules.GoalKind
import com.companionstudio.nyx.ui.labelRes
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TrendsRoute(repository: TrendsRepository) {
    val viewModel = viewModel { TrendsViewModel(repository) }
    val range by viewModel.range.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    TrendsScreen(range, days, onRange = viewModel::show)
}

// Items above the day rows: title, range toggle, chart, legend
private const val ITEMS_BEFORE_DAYS = 4

/** S-10 (US-18, D-152). [days] is `null` while loading, oldest first; the rows show them newest first. */
@Composable
fun TrendsScreen(range: TrendRange, days: List<TrendDay>?, onRange: (TrendRange) -> Unit) {
    Scaffold { innerPadding ->
        if (days == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val expanded = remember { mutableStateMapOf<LocalDate, Boolean>() }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val newestFirst = days.reversed()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.trends_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
            item { RangeToggle(range, onRange) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrendsChart(days, range, onDayTap = { index ->
                        expanded[days[index].day] = true
                        scope.launch { listState.animateScrollToItem(ITEMS_BEFORE_DAYS + days.lastIndex - index) }
                    })
                    if (days.none { it.tracked }) Text(stringResource(R.string.trends_empty))
                }
            }
            item { Legend() }
            items(newestFirst, key = { it.day.toString() }) { day ->
                DayRow(day, expanded[day.day] == true, onToggle = { expanded[day.day] = expanded[day.day] != true })
            }
        }
    }
}

@Composable
private fun RangeToggle(range: TrendRange, onRange: (TrendRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        TrendRange.entries.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == range,
                onClick = { onRange(option) },
                shape = SegmentedButtonDefaults.itemShape(i, TrendRange.entries.size),
            ) {
                Text(stringResource(if (option == TrendRange.WEEK) R.string.trends_week else R.string.trends_month))
            }
        }
    }
}

/** Names every colour, so the chart never relies on colour alone (§9). */
@Composable
private fun Legend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Category.entries.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Swatch(category)
                Text(stringResource(category.labelRes), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Swatch(category: Category) {
    Box(Modifier.size(12.dp).background(categoryColor(category), RoundedCornerShape(2.dp)))
}

/** One day: date, total, goals met; expanded, time per category and each goal in words. The chart's table view. */
@Composable
private fun DayRow(day: TrendDay, expanded: Boolean, onToggle: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val date = if (day.isToday) stringResource(R.string.trends_today) else day.day.format(DateTimeFormatter.ofPattern(DATE_PATTERN, locale))
    val state = stringResource(if (expanded) R.string.trends_expanded else R.string.trends_collapsed)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = day.tracked, role = Role.Button, onClick = onToggle)
            .semantics { if (day.tracked) stateDescription = state },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(date, style = MaterialTheme.typography.titleSmall)
            if (!day.tracked) {
                Text(stringResource(R.string.trends_not_tracked))
                return@Column
            }
            val goals = when {
                day.goals.isEmpty() -> stringResource(R.string.trends_no_goals)
                else -> stringResource(R.string.trends_goals_met, day.goalsAchieved, day.goals.size)
            }
            Text(stringResource(R.string.trends_day_summary, formatDuration(day.totalSeconds), goals))
            if (expanded) DayDetails(day)
        }
    }
}

@Composable
private fun DayDetails(day: TrendDay) {
    Category.entries.forEach { category ->
        val seconds = day.seconds[category] ?: return@forEach
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch(category)
            Text(stringResource(R.string.trends_category_time, stringResource(category.labelRes), formatDuration(seconds)))
        }
    }
    day.goals.forEach { result ->
        Text(stringResource(R.string.trends_goal_result, goalLabel(result.goal), goalOutcome(result, day.isToday)))
    }
}

/** "Social & scrolling ≤ 1 h", "Focus sessions ≥ 2", like Home's rows. */
@Composable
private fun goalLabel(goal: Goal): String = when (goal.kind) {
    GoalKind.LIMIT -> stringResource(
        R.string.home_goal_label,
        goal.category?.let { stringResource(it.labelRes) } ?: stringResource(R.string.home_goal_total),
        formatDuration(goal.target * 60L),
    )
    GoalKind.FOCUS_SESSIONS -> stringResource(R.string.home_goal_at_least, stringResource(R.string.home_goal_focus_sessions), goal.target.toString())
    GoalKind.PRODUCTIVE_MINUTES -> stringResource(R.string.home_goal_at_least, stringResource(Category.PRODUCTIVE.labelRes), formatDuration(goal.target * 60L))
}

/** Kept / missed for limits, met / not met for positive goals; today is judged so far (D-149). */
@Composable
private fun goalOutcome(result: GoalResult, today: Boolean): String = stringResource(
    when {
        !result.goal.isPositive && result.achieved -> if (today) R.string.trends_goal_on_track else R.string.trends_goal_kept
        !result.goal.isPositive -> R.string.trends_goal_missed
        result.achieved -> R.string.trends_goal_met
        else -> if (today) R.string.trends_goal_not_yet else R.string.trends_goal_not_met
    },
)

private const val DATE_PATTERN = "EEE d MMM"

@Preview(showBackground = true)
@Composable
private fun TrendsScreenPreview() {
    val today = LocalDate.parse("2026-10-23")
    val days = (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        if (back > 4) TrendDay(day, false, false, emptyMap(), emptyList())
        else TrendDay(
            day,
            isToday = back == 0,
            tracked = true,
            seconds = mapOf(Category.SOCIAL to 3000L + back * 900, Category.PRODUCTIVE to 2400L, Category.NEUTRAL to 1800L),
            goals = listOf(GoalResult(Goal(GoalKind.LIMIT, Category.SOCIAL, 60), achieved = back % 2 == 0)),
        )
    }
    NyxTheme { TrendsScreen(TrendRange.WEEK, days, onRange = {}) }
}
