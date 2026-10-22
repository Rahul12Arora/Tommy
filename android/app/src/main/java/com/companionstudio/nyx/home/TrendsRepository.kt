package com.companionstudio.nyx.home

import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.rules.DailyGoalDao
import com.companionstudio.nyx.rules.PetEventDao
import com.companionstudio.nyx.tracking.DailyUsageDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** S-10's days from Room (§14.13). Live: today's bar grows as the tracker credits, and it rolls over at the reset. */
class TrendsRepository(
    private val usage: DailyUsageDao,
    private val events: PetEventDao,
    private val goals: DailyGoalDao,
    private val config: Flow<EffectiveConfig>,
    private val dayClock: DayClock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(range: TrendRange): Flow<List<TrendDay>> = config
        .map { checkNotNull(it.valueOf(SettingPaths.RESET_HOUR)) }
        .distinctUntilChanged()
        .flatMapLatest { resetHour -> dayClock.todayUpdates(resetHour) }
        .flatMapLatest { today ->
            val from = today.minusDays(range.days - 1L).toString()
            combine(
                usage.observeTotalsSince(from),
                goals.observeSince(from),
                events.observeGoalEventsSince(from),
                goals.observeFirstDay(),
            ) { totals, dayGoals, goalEvents, firstDay ->
                trendDays(today, range, firstDay?.let(LocalDate::parse), totals, dayGoals, goalEvents)
            }
        }
}
