package com.companionstudio.nyx.debug

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.rules.DailyGoal
import com.companionstudio.nyx.rules.Goal
import com.companionstudio.nyx.rules.GoalKind
import com.companionstudio.nyx.rules.PetEvent
import com.companionstudio.nyx.rules.PetEventType
import com.companionstudio.nyx.rules.positiveGoalsReached
import com.companionstudio.nyx.tracking.DailyUsage
import java.time.LocalDate
import kotlin.random.Random

/** What `--es seed trends-30d` writes (D-155), and one line per day for the manual check to compare with S-10. */
class TrendSeed(val usage: List<DailyUsage>, val goals: List<DailyGoal>, val events: List<PetEvent>, val lines: List<String>)

/** Days seeded before today; the 30-day view then starts with 30 − 1 − 24 = 5 untracked days. */
const val SEEDED_DAYS = 24

private const val ZERO_DAY = 3
private const val LONG_DAY = 10
private const val ALL_MET_DAY = 5
private const val NONE_MET_DAY = 7

// Positive goals are switched on from this many days back, so older days show only the Social limit
private const val GOALS_FROM = 14

private val SOCIAL_LIMIT = Goal(GoalKind.LIMIT, Category.SOCIAL, 60)
private val FOCUS_GOAL = Goal(GoalKind.FOCUS_SESSIONS, null, 2)
private val PRODUCTIVE_GOAL = Goal(GoalKind.PRODUCTIVE_MINUTES, null, 60)

private val PACKAGES = mapOf(
    Category.SOCIAL to "com.instagram.android",
    Category.GAMES to "com.king.candycrushsaga",
    Category.ENTERTAINMENT to "com.google.android.youtube",
    Category.PRODUCTIVE to "com.google.android.apps.docs",
    Category.NEUTRAL to "com.android.chrome",
)

/**
 * 24 past days ending yesterday (today is left to the tracker). Fixed random seed: the same data on every run. Events
 * are judged by the engine's own rules: `LIMIT_EXCEEDED` once Social reaches its limit, `GOAL_MET` by
 * [positiveGoalsReached].
 */
fun trendSeed(today: LocalDate, random: Random = Random(13)): TrendSeed {
    val usage = mutableListOf<DailyUsage>()
    val goals = mutableListOf<DailyGoal>()
    val events = mutableListOf<PetEvent>()
    val lines = mutableListOf<String>()
    for (back in SEEDED_DAYS downTo 1) {
        val day = today.minusDays(back.toLong()).toString()
        val minutes = minutesFor(back, random)
        val sessions = when (back) {
            ALL_MET_DAY -> 2
            NONE_MET_DAY -> 0
            else -> random.nextInt(0, 4)
        }
        val dayGoals = if (back <= GOALS_FROM) listOf(SOCIAL_LIMIT, FOCUS_GOAL, PRODUCTIVE_GOAL) else listOf(SOCIAL_LIMIT)

        minutes.forEach { (category, value) -> usage += DailyUsage(day, PACKAGES.getValue(category), category, value * 60_000) }
        dayGoals.forEach { goals += DailyGoal(day, it.key, it.target) }
        val dayEvents = mutableListOf<PetEvent>()
        repeat(sessions) { dayEvents += PetEvent(day = day, atMs = 0, type = PetEventType.FOCUS_COMPLETED) }
        val socialOver = (minutes[Category.SOCIAL] ?: 0) >= SOCIAL_LIMIT.target
        if (socialOver) dayEvents += PetEvent(day = day, atMs = 0, type = PetEventType.LIMIT_EXCEEDED, subject = SOCIAL_LIMIT.key)
        val met = positiveGoalsReached(dayGoals, minutes.mapValues { it.value * 60_000 }, dayEvents)
        met.forEach { dayEvents += PetEvent(day = day, atMs = 0, type = PetEventType.GOAL_MET, subject = it.key) }
        events += dayEvents

        val achieved = met.size + if (socialOver) 0 else 1
        lines += "$day: ${formatDuration(minutes.values.sum() * 60)}, goals $achieved of ${dayGoals.size}"
    }
    return TrendSeed(usage, goals, events, lines)
}

/** Minutes per category for the day [back] days ago; categories with 0 are left out. */
private fun minutesFor(back: Int, random: Random): Map<Category, Long> {
    val minutes = when (back) {
        ZERO_DAY -> emptyMap()
        LONG_DAY -> mapOf(Category.SOCIAL to 150L, Category.GAMES to 120L, Category.ENTERTAINMENT to 180L, Category.PRODUCTIVE to 60L, Category.NEUTRAL to 30L)
        ALL_MET_DAY -> mapOf(Category.SOCIAL to 40L, Category.PRODUCTIVE to 90L, Category.NEUTRAL to 25L)
        NONE_MET_DAY -> mapOf(Category.SOCIAL to 100L, Category.ENTERTAINMENT to 70L, Category.PRODUCTIVE to 20L)
        else -> mapOf(
            Category.SOCIAL to random.nextLong(20, 111),
            Category.GAMES to random.nextLong(0, 61),
            Category.ENTERTAINMENT to random.nextLong(10, 91),
            Category.PRODUCTIVE to random.nextLong(10, 121),
            Category.NEUTRAL to random.nextLong(10, 61),
        )
    }
    return minutes.filterValues { it > 0 }
}
