package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import java.time.LocalDate

enum class GoalKind { LIMIT, FOCUS_SESSIONS, PRODUCTIVE_MINUTES }

/** The keys positive goals are logged and recorded under (§14.13); limit goals use their [LimitKeys]. */
object GoalKeys {
    const val FOCUS_SESSIONS = "goal:focus_sessions"
    const val PRODUCTIVE_MINUTES = "goal:productive_minutes"
}

/**
 * A goal that's on (US-17). [target] is minutes, or sessions for [GoalKind.FOCUS_SESSIONS]. [category] is only for
 * [GoalKind.LIMIT], where `null` is the total limit.
 */
data class Goal(val kind: GoalKind, val category: Category?, val target: Int) {
    val key: String
        get() = when (kind) {
            GoalKind.LIMIT -> category?.let(LimitKeys::category) ?: LimitKeys.TOTAL
            GoalKind.FOCUS_SESSIONS -> GoalKeys.FOCUS_SESSIONS
            GoalKind.PRODUCTIVE_MINUTES -> GoalKeys.PRODUCTIVE_MINUTES
        }

    val isPositive: Boolean get() = kind != GoalKind.LIMIT
}

/** The limits that are on in Home's order (categories, then the total), then the positive goals that are on. */
fun goalsOn(config: EffectiveConfig): List<Goal> = buildList {
    for (category in Category.entries) {
        val limit = config.valueOf(SettingPaths.categoryDailyMinutes(category)) ?: continue
        if (limit > 0) add(Goal(GoalKind.LIMIT, category, limit))
    }
    val total = checkNotNull(config.valueOf(SettingPaths.TOTAL_DAILY_MINUTES))
    if (total > 0) add(Goal(GoalKind.LIMIT, null, total))
    val sessions = checkNotNull(config.valueOf(SettingPaths.GOAL_FOCUS_SESSIONS))
    if (sessions > 0) add(Goal(GoalKind.FOCUS_SESSIONS, null, sessions))
    val productive = checkNotNull(config.valueOf(SettingPaths.GOAL_PRODUCTIVE_MINUTES))
    if (productive > 0) add(Goal(GoalKind.PRODUCTIVE_MINUTES, null, productive))
}

/** The goal a stored [key] stands for; `null` for a key this build doesn't know, which is then skipped. */
fun goalOf(key: String, target: Int): Goal? = when (key) {
    GoalKeys.FOCUS_SESSIONS -> Goal(GoalKind.FOCUS_SESSIONS, null, target)
    GoalKeys.PRODUCTIVE_MINUTES -> Goal(GoalKind.PRODUCTIVE_MINUTES, null, target)
    LimitKeys.TOTAL -> Goal(GoalKind.LIMIT, null, target)
    else -> Category.entries.firstOrNull { LimitKeys.category(it) == key }?.let { Goal(GoalKind.LIMIT, it, target) }
}

/** Today's progress towards a positive goal: completed sessions (D-150), or Productive milliseconds. */
fun positiveProgress(goal: Goal, todayMs: Map<Category, Long>, logged: List<PetEvent>): Long = when (goal.kind) {
    GoalKind.FOCUS_SESSIONS -> logged.count { it.type == PetEventType.FOCUS_COMPLETED }.toLong()
    GoalKind.PRODUCTIVE_MINUTES -> todayMs[Category.PRODUCTIVE] ?: 0
    GoalKind.LIMIT -> error("a limit goal has no positive progress")
}

/** The positive goals in [goals] reached today that [logged] (today's log) has no `GOAL_MET` for yet (US-17). */
fun positiveGoalsReached(goals: List<Goal>, todayMs: Map<Category, Long>, logged: List<PetEvent>): List<Goal> {
    val metToday = logged.filter { it.type == PetEventType.GOAL_MET }.mapNotNull { it.subject }.toSet()
    return goals.filter { goal ->
        val needed = if (goal.kind == GoalKind.PRODUCTIVE_MINUTES) goal.target * 60_000L else goal.target.toLong()
        goal.isPositive && goal.key !in metToday && positiveProgress(goal, todayMs, logged) >= needed
    }
}

/**
 * The goals' part of a tick (§14.13): records which goals are on today when that changed (D-148), and returns the
 * positive goals met just now. Used only from the engine coroutine, like [Reactions].
 */
class GoalStep(private val dao: DailyGoalDao) {
    // What this run last wrote; after a restart the first tick writes again, which replaces the same rows
    private var recorded: List<DailyGoal>? = null

    suspend fun afterTick(day: LocalDate, config: EffectiveConfig, todayMs: Map<Category, Long>, logged: List<PetEvent>): List<Goal> {
        val goals = goalsOn(config)
        val rows = goals.map { DailyGoal(day.toString(), it.key, it.target) }
        if (rows != recorded) {
            dao.replaceDay(day.toString(), rows)
            recorded = rows
        }
        return positiveGoalsReached(goals, todayMs, logged)
    }

    suspend fun deleteBefore(day: LocalDate) = dao.deleteBefore(day.toString())
}
