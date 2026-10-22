package com.companionstudio.nyx.home

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.rules.DailyGoal
import com.companionstudio.nyx.rules.Goal
import com.companionstudio.nyx.rules.PetEvent
import com.companionstudio.nyx.rules.PetEventType
import com.companionstudio.nyx.rules.goalOf
import com.companionstudio.nyx.tracking.DayCategoryTotal
import java.time.LocalDate

/** S-10's two ranges (US-18). */
enum class TrendRange(val days: Int) { WEEK(7), MONTH(30) }

/**
 * One day on S-10. [tracked] `false`: before Nyx was tracking, shown as empty rather than zero (D-151); it then has no
 * [seconds] or [goals]. [seconds] leaves out categories with no time.
 */
data class TrendDay(
    val day: LocalDate,
    val isToday: Boolean,
    val tracked: Boolean,
    val seconds: Map<Category, Long>,
    val goals: List<GoalResult>,
) {
    val totalSeconds: Long get() = seconds.values.sum()
    val goalsAchieved: Int get() = goals.count { it.achieved }
}

/** A limit goal kept, or a positive goal met; today's are "so far" (D-149). */
data class GoalResult(val goal: Goal, val achieved: Boolean)

/**
 * The [range]'s days ending [today], oldest first (§14.13). A day's goals are the ones recorded for it (D-148); a limit
 * goal is kept unless that day logged `LIMIT_EXCEEDED` for it, a positive goal met if it logged `GOAL_MET` for it.
 */
fun trendDays(
    today: LocalDate,
    range: TrendRange,
    firstDay: LocalDate?,
    totals: List<DayCategoryTotal>,
    goals: List<DailyGoal>,
    goalEvents: List<PetEvent>,
): List<TrendDay> {
    val totalsByDay = totals.groupBy { it.day }
    val goalsByDay = goals.groupBy { it.day }
    val eventsByDay = goalEvents.groupBy { it.day }
    return (range.days - 1 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        val key = day.toString()
        if (firstDay == null || day < firstDay) return@map TrendDay(day, back == 0, tracked = false, emptyMap(), emptyList())

        val seconds = totalsByDay[key].orEmpty()
            .groupBy { it.category }
            .mapValues { (_, parts) -> parts.sumOf { it.durationMs } / 1000 }
            .filterValues { it > 0 }
        val events = eventsByDay[key].orEmpty()
        val results = goalsByDay[key].orEmpty().mapNotNull { row ->
            val goal = goalOf(row.goalKey, row.target) ?: return@mapNotNull null
            val type = if (goal.isPositive) PetEventType.GOAL_MET else PetEventType.LIMIT_EXCEEDED
            val logged = events.any { it.type == type && it.subject == goal.key }
            GoalResult(goal, achieved = if (goal.isPositive) logged else !logged)
        }.sortedWith(HOME_ORDER)
        TrendDay(day, back == 0, tracked = true, seconds, results)
    }
}

// Rows come back in no particular order; show them like Home: category limits, the total, then positive goals
private val HOME_ORDER = compareBy<GoalResult>({ it.goal.kind }, { it.goal.category?.ordinal ?: Int.MAX_VALUE })
