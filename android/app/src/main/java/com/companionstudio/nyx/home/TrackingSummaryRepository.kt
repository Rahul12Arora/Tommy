package com.companionstudio.nyx.home

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.mood.bandAt
import com.companionstudio.nyx.mood.moodOf
import com.companionstudio.nyx.pet.PetChoice
import com.companionstudio.nyx.pet.PetSettings
import com.companionstudio.nyx.rules.PetEvent
import com.companionstudio.nyx.rules.PetEventDao
import com.companionstudio.nyx.tracking.DailyUsage
import com.companionstudio.nyx.tracking.DailyUsageDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

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
        today(resetHour).flatMapLatest { day ->
            combine(dao.observeDay(day.toString()), events.observeDay(day.toString()), petSettings.choice) { rows, logged, choice ->
                todaySummary(rows, logged, effective, choice)
            }
        }
    }

    // Checked every minute: cheap, and Home turns over to the new day within a minute of the reset
    private fun today(resetHour: Int): Flow<LocalDate> = flow {
        while (true) {
            emit(dayClock.today(resetHour))
            delay(60_000)
        }
    }.distinctUntilChanged()
}

/** Totals by category, the limit goals that are on, and the chosen pet's face for today's mood (US-15). */
fun todaySummary(rows: List<DailyUsage>, events: List<PetEvent>, config: EffectiveConfig, choice: PetChoice): TodaySummary {
    val document = config.document
    val byCategory = rows.groupBy { it.category }.mapValues { (_, parts) -> parts.sumOf { it.durationMs } / 1000 }
    val totalSeconds = byCategory.values.sum()

    val goals = buildList {
        for (category in Category.entries) {
            val limit = config.valueOf(SettingPaths.categoryDailyMinutes(category)) ?: continue
            if (limit > 0) add(GoalProgress(category, byCategory[category] ?: 0, limit * 60L))
        }
        val totalLimit = checkNotNull(config.valueOf(SettingPaths.TOTAL_DAILY_MINUTES))
        if (totalLimit > 0) add(GoalProgress(category = null, totalSeconds, totalLimit * 60L))
    }

    val pet = document.pets.firstOrNull { it.id == choice.petId && it.enabled } ?: document.pets.first { it.enabled }
    val band = document.mood.bandAt(moodOf(events.map { it.type }, document.mood)).id
    return TodaySummary(
        totalSeconds = totalSeconds,
        productiveSeconds = byCategory[Category.PRODUCTIVE] ?: 0,
        doomscrollSeconds = document.tracking.doomscrollCategories.sumOf { byCategory[it] ?: 0 },
        goals = goals,
        petFace = pet.faces.getValue(band),
    )
}
