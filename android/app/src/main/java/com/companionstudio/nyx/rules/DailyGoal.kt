package com.companionstudio.nyx.rules

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * A goal that was on during [day] (D-148), so Trends judges each day by its own goals. [goalKey] is [Goal.key];
 * [target] is minutes, or sessions for the focus goal.
 */
@Entity(tableName = "daily_goals", primaryKeys = ["day", "goal_key"])
data class DailyGoal(
    val day: String,
    @ColumnInfo(name = "goal_key") val goalKey: String,
    val target: Int,
)
