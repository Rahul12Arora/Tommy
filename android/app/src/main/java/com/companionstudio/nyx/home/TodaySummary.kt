package com.companionstudio.nyx.home

/** What Home (S-08) shows about today. */
data class TodaySummary(
    val totalSeconds: Long,
    val productiveSeconds: Long,
    val doomscrollSeconds: Long,
    val goals: List<GoalProgress>,
    val petFace: String,
)

data class GoalProgress(val label: String, val usedSeconds: Long, val targetSeconds: Long) {
    val isOver: Boolean get() = usedSeconds > targetSeconds
}
