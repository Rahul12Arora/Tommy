package com.companionstudio.nyx.tracking

import android.util.Log
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.NyxConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.data.RETENTION_DAYS
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where tracking stands after a tick: what the rules engine (M7) and the tick interval work from. */
data class TrackerSnapshot(
    val day: LocalDate,
    /** The counted app in front; `null` when nothing counted is in front or the screen is off. */
    val frontPackage: String?,
    val frontCategory: Category?,
    val stretchMs: Long,
    val todayMs: Map<Category, Long>,
)

/**
 * Turns usage events into daily totals (§14.4). Holds the state between ticks; one instance per service run,
 * used only from the engine coroutine. After a restart the first tick replays the log from before the stored
 * cursor, so time used while the service was dead is still credited, and nothing twice (D-62).
 */
class Tracker(
    private val source: UsageSource,
    private val dao: DailyUsageDao,
    private val clock: Clock,
    private val dayClock: DayClock,
    private val excluded: Set<String>,
    private val androidCategoryOf: (packageName: String) -> Int,
) {
    private var state: TrackerState? = null
    private var creditedUntil: Instant? = null
    private var cleanedUpDay: LocalDate? = null
    private val androidCategories = HashMap<String, Int>()

    suspend fun tick(config: EffectiveConfig, choices: Map<String, Category>): TrackerSnapshot {
        // Whole milliseconds, like the event log and the stored cursor, so no fractions are lost between ticks
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val resetHour = checkNotNull(config.valueOf(SettingPaths.RESET_HOUR))
        val creditFrom = creditedUntil ?: storedCursor(now, resetHour)
        val previous = state ?: TrackerState(at = replayStart(creditFrom, now, resetHour))
        val rules = CreditRules(
            creditFrom = creditFrom,
            dayOf = { dayClock.dayKeyAt(it, resetHour) },
            nextDayStart = { dayClock.nextDayStart(it, resetHour) },
            isCounted = { it !in excluded },
            categoryOf = { categoryOf(it, choices, config.document) },
            stretchCategories = config.document.limits.stretch.categories.toSet(),
            breakMs = config.document.breaks.breakMinutes * 60_000L,
        )
        // If the clock went back, nothing is credited until it passes the cursor again
        val events = if (now > previous.at) source.events(previous.at, now) else emptyList()
        val result = advance(previous, events, now, rules)
        dao.record(
            credits = result.credits.map { DailyUsage(it.day.toString(), it.packageName, it.category, it.durationMs) },
            creditedUntilMs = result.state.at.toEpochMilli(),
        )
        state = result.state
        creditedUntil = result.state.at

        val today = dayClock.dayKeyAt(result.state.at, resetHour)
        if (today != cleanedUpDay) {
            dao.deleteBefore(today.minusDays(RETENTION_DAYS).toString())
            cleanedUpDay = today
        }
        return snapshot(result.state, today, rules)
    }

    /** The first start ever credits from the start of today; a very old cursor is cut to the catch-up limit. */
    private suspend fun storedCursor(now: Instant, resetHour: Int): Instant {
        val stored = dao.creditedUntilMs()?.let(Instant::ofEpochMilli)
            ?: return dayClock.startOfDay(dayClock.dayKeyAt(now, resetHour), resetHour)
        val oldest = now.minus(MAX_CATCH_UP)
        if (stored >= oldest) return stored
        Log.w(TAG, "Tracking was off since $stored; catching up from $oldest only")
        return oldest
    }

    /** Far enough back to know which app was in front at the cursor, and to rebuild today's stretch. */
    private fun replayStart(creditFrom: Instant, now: Instant, resetHour: Int): Instant {
        val startOfToday = dayClock.startOfDay(dayClock.dayKeyAt(now, resetHour), resetHour)
        return minOf(creditFrom, startOfToday).minus(REPLAY_LOOKBACK)
    }

    private fun categoryOf(packageName: String, choices: Map<String, Category>, document: NyxConfig): Category =
        choices[packageName]
            ?: suggestCategory(packageName, androidCategories.getOrPut(packageName) { androidCategoryOf(packageName) }, document)

    private suspend fun snapshot(state: TrackerState, today: LocalDate, rules: CreditRules): TrackerSnapshot {
        val front = state.front?.packageName?.takeIf { state.screenOn && rules.isCounted(it) }
        return TrackerSnapshot(
            day = today,
            frontPackage = front,
            frontCategory = front?.let(rules.categoryOf),
            stretchMs = if (state.stretchDay == today) state.stretchMs else 0,
            todayMs = dao.categoryTotals(today.toString()).associate { it.category to it.durationMs },
        )
    }

    private companion object {
        const val TAG = "Nyx"
        val MAX_CATCH_UP: Duration = Duration.ofDays(7) // Android keeps the event log for about this long
        val REPLAY_LOOKBACK: Duration = Duration.ofHours(24)
    }
}
