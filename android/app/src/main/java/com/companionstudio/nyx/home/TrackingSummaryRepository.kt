package com.companionstudio.nyx.home

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.mood.bandAt
import com.companionstudio.nyx.mood.moodOf
import com.companionstudio.nyx.pet.PetChoice
import com.companionstudio.nyx.pet.PetSettings
import com.companionstudio.nyx.rules.GoalKind
import com.companionstudio.nyx.rules.PetEvent
import com.companionstudio.nyx.rules.PetEventDao
import com.companionstudio.nyx.rules.goalsOn
import com.companionstudio.nyx.rules.positiveProgress
import com.companionstudio.nyx.tracking.DailyUsage
import com.companionstudio.nyx.tracking.DailyUsageDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Today's real numbers from the tracker's table (§14.4) and the pet's face from the event log (§14.6).
 * Updates after every tick and rolls over at the reset hour.
 */
class TrackingSummaryRepository(
    private val dao: DailyUsageDao,
    private val events: PetEventDao,
    private val config: Flow<EffectiveConfig>,
    private val petSettings: PetSettings,
    private val dayClock: DayClock,
) : SummaryRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeToday(): Flow<TodaySummary> = config.flatMapLatest { effective ->
        val resetHour = checkNotNull(effective.valueOf(SettingPaths.RESET_HOUR))
        dayClock.todayUpdates(resetHour).flatMapLatest { day ->
            combine(dao.observeDay(day.toString()), events.observeDay(day.toString()), petSettings.choice) { rows, logged, choice ->
                todaySummary(rows, logged, effective, choice)
            }
        }
    }
}

/** Totals by category, the goals that are on, and the chosen pet's face for today's mood (US-15). */
fun todaySummary(rows: List<DailyUsage>, events: List<PetEvent>, config: EffectiveConfig, choice: PetChoice): TodaySummary {
    val document = config.document
    val msByCategory = rows.groupBy { it.category }.mapValues { (_, parts) -> parts.sumOf { it.durationMs } }
    val byCategory = msByCategory.mapValues { it.value / 1000 }
    val totalSeconds = byCategory.values.sum()

    val (positive, limits) = goalsOn(config).partition { it.isPositive }
    val goals = limits.map { goal ->
        val used = if (goal.category == null) totalSeconds else byCategory[goal.category] ?: 0
        GoalProgress(goal.category, used, goal.target * 60L)
    }
    val positiveGoals = positive.map { goal ->
        val progress = positiveProgress(goal, msByCategory, events)
        if (goal.kind == GoalKind.PRODUCTIVE_MINUTES) PositiveGoalProgress(goal, progress / 1000, goal.target * 60L)
        else PositiveGoalProgress(goal, progress, goal.target.toLong())
    }

    val pet = document.pets.firstOrNull { it.id == choice.petId && it.enabled } ?: document.pets.first { it.enabled }
    val band = document.mood.bandAt(moodOf(events.map { it.type }, document.mood)).id
    return TodaySummary(
        totalSeconds = totalSeconds,
        productiveSeconds = byCategory[Category.PRODUCTIVE] ?: 0,
        doomscrollSeconds = document.tracking.doomscrollCategories.sumOf { byCategory[it] ?: 0 },
        goals = goals,
        petFace = pet.faces.getValue(band),
        positiveGoals = positiveGoals,
    )
}
