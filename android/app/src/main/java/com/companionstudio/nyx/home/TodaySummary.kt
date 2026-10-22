package com.companionstudio.nyx.home

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.rules.Goal
import com.companionstudio.nyx.rules.GoalKind

/** What Home (S-08) shows about today. */
data class TodaySummary(
    val totalSeconds: Long,
    val productiveSeconds: Long,
    val doomscrollSeconds: Long,
    val goals: List<GoalProgress>,
    val petFace: String,
    val positiveGoals: List<PositiveGoalProgress> = emptyList(),
)

/** A limit goal: [category]'s daily limit, or the total limit when it's `null` (D-68). */
data class GoalProgress(val category: Category?, val usedSeconds: Long, val targetSeconds: Long) {
    val isOver: Boolean get() = usedSeconds > targetSeconds
}

/**
 * A positive goal that's on (US-17, D-147). [used] and [target] are seconds for the productive goal and sessions for
 * the focus goal.
 */
data class PositiveGoalProgress(val goal: Goal, val used: Long, val target: Long) {
    val isMet: Boolean get() = used >= target

    val inSeconds: Boolean get() = goal.kind == GoalKind.PRODUCTIVE_MINUTES
}
