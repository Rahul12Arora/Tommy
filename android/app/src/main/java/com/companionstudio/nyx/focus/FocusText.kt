package com.companionstudio.nyx.focus

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.companionstudio.nyx.R
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.home.formatShort

/**
 * The seconds the timer shows (D-118): left for timed goals, rounded up to a whole minute so "0 min left" never shows
 * while time remains; so far for until done.
 */
fun focusTimerSeconds(status: FocusStatus): Long {
    val goalMs = status.goalMinutes?.takeIf { status.goal != FocusGoal.UNTIL_DONE }?.let { it * 60_000L }
        ?: return status.focusMs / 1000
    val leftMs = (goalMs - status.focusMs).coerceAtLeast(0)
    return (leftMs + 59_999) / 60_000 * 60
}

/** For the ring slot and the pet's badge: "12m", "!12m" while away (shown by more than colour, §9). */
fun focusShortText(status: FocusStatus): String {
    val time = formatShort(focusTimerSeconds(status))
    return if (status.phase == FocusPhase.DRIFTING) "!$time" else time
}

/** "12 min left" or "34 min so far". */
@Composable
fun focusTimeText(status: FocusStatus): String {
    val time = formatDuration(focusTimerSeconds(status))
    return stringResource(if (status.goal == FocusGoal.UNTIL_DONE) R.string.focus_so_far else R.string.focus_left, time)
}

/** "12 min left · switched away twice · away from your focus apps", for captions and TalkBack. */
@Composable
fun focusSummaryText(status: FocusStatus): String = buildList {
    add(focusTimeText(status))
    if (status.switches > 0) add(pluralStringResource(R.plurals.focus_switches, status.switches, status.switches))
    if (status.phase == FocusPhase.DRIFTING) add(stringResource(R.string.focus_away))
}.joinToString(" · ")

@Composable
fun goalName(goal: FocusGoal): String = stringResource(
    when (goal) {
        FocusGoal.FIXED_TIME -> R.string.focus_goal_fixed_time
        FocusGoal.UNTIL_DONE -> R.string.focus_goal_until_done
        FocusGoal.NO_SWITCHING -> R.string.focus_goal_no_switching
    },
)
