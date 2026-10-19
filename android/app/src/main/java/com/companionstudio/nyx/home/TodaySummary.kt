package com.companionstudio.nyx.home

import com.companionstudio.nyx.config.Category

/** What Home (S-08) shows about today. */
data class TodaySummary(
    val totalSeconds: Long,
    val productiveSeconds: Long,
    val doomscrollSeconds: Long,
    val goals: List<GoalProgress>,
    val petFace: String,
)

/** A limit goal: [category]'s daily limit, or the total limit when it's `null` (D-68). */
data class GoalProgress(val category: Category?, val usedSeconds: Long, val targetSeconds: Long) {
    val isOver: Boolean get() = usedSeconds > targetSeconds
}
