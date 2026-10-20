package com.companionstudio.nyx.tracking

import com.companionstudio.nyx.config.Category
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One resumed activity. Two screens of the same app are different activities of the same package. */
data class ActivityKey(val packageName: String, val className: String)

/**
 * What the tracker remembers between ticks. After a restart it's rebuilt by replaying the event log (D-62).
 * [at]: events before this moment have been applied.
 */
data class TrackerState(
    val at: Instant,
    val front: ActivityKey? = null,
    val screenOn: Boolean = true,
    val screenOffSince: Instant? = null,
    val stretchMs: Long = 0,
    val stretchDay: LocalDate? = null,
)

/** Everything crediting needs from the config, the user's choices, the clock and the device. */
class CreditRules(
    /** The cursor: time before it was credited earlier, so it's only replayed. */
    val creditFrom: Instant,
    val dayOf: (Instant) -> LocalDate,
    val nextDayStart: (Instant) -> Instant,
    val isCounted: (packageName: String) -> Boolean,
    val categoryOf: (packageName: String) -> Category,
    val stretchCategories: Set<Category>,
    val breakMs: Long,
)

data class Credit(val day: LocalDate, val packageName: String, val category: Category, val durationMs: Long)

data class Advanced(val state: TrackerState, val credits: List<Credit>)

/**
 * Applies [events] (oldest first) and credits time up to [until] (§14.4). The time between two events belongs to the
 * app in front at the start of it, if the screen is on and the app is counted. A segment that crosses the reset hour
 * is split between the two days. Credits for the same day, app and category are added together.
 */
fun advance(state: TrackerState, events: List<UsageEvent>, until: Instant, rules: CreditRules): Advanced {
    val credits = mutableListOf<Credit>()
    var current = state
    for (event in events) {
        if (event.at > until) break
        current = creditUntil(current, event.at, rules, credits)
        current = apply(current, event, rules.breakMs)
    }
    current = creditUntil(current, until, rules, credits)
    val merged = credits
        .groupBy { Triple(it.day, it.packageName, it.category) }
        .map { (key, parts) -> Credit(key.first, key.second, key.third, parts.sumOf { it.durationMs }) }
    return Advanced(current, merged)
}

private fun creditUntil(state: TrackerState, to: Instant, rules: CreditRules, credits: MutableList<Credit>): TrackerState {
    var current = state
    var from = state.at
    while (from < to) {
        val pieceEnd = minOf(to, rules.nextDayStart(from))
        current = creditPiece(current, from, pieceEnd, rules, credits)
        from = pieceEnd
    }
    return if (to > current.at) current.copy(at = to) else current
}

/** [from]..[to] lies within one day. */
private fun creditPiece(
    state: TrackerState,
    from: Instant,
    to: Instant,
    rules: CreditRules,
    credits: MutableList<Credit>,
): TrackerState {
    val day = rules.dayOf(from)
    // The day reset starts a new stretch (§9)
    val current = if (state.stretchDay == day) state else state.copy(stretchMs = 0, stretchDay = day)
    val packageName = current.front?.packageName
    if (!current.screenOn || packageName == null || !rules.isCounted(packageName)) return current

    val category = rules.categoryOf(packageName)
    val creditedFrom = maxOf(from, rules.creditFrom)
    if (creditedFrom < to) credits += Credit(day, packageName, category, Duration.between(creditedFrom, to).toMillis())
    // Replayed time still builds the stretch, so it survives a restart
    return if (category in rules.stretchCategories) {
        current.copy(stretchMs = current.stretchMs + Duration.between(from, to).toMillis())
    } else {
        current
    }
}

private fun apply(state: TrackerState, event: UsageEvent, breakMs: Long): TrackerState = when (event.type) {
    UsageEventType.APP_RESUMED -> state.copy(front = ActivityKey(event.packageName, event.className))
    // Only the front activity's own pause ends it, so a late pause from the previous screen changes nothing
    UsageEventType.APP_PAUSED ->
        if (state.front == ActivityKey(event.packageName, event.className)) state.copy(front = null) else state
    UsageEventType.SCREEN_OFF ->
        if (state.screenOn) state.copy(screenOn = false, screenOffSince = event.at) else state
    UsageEventType.SCREEN_ON -> screenOn(state, event.at, breakMs)
    UsageEventType.DEVICE_STARTUP -> screenOn(state.copy(front = null), event.at, breakMs)
    UsageEventType.DEVICE_SHUTDOWN ->
        state.copy(front = null, screenOn = false, screenOffSince = state.screenOffSince ?: event.at)
}

/** A screen-off gap of at least the break length is a break, which starts a new stretch (D-27, D-65). */
private fun screenOn(state: TrackerState, at: Instant, breakMs: Long): TrackerState {
    val offSince = state.screenOffSince
    val tookBreak = offSince != null && Duration.between(offSince, at).toMillis() >= breakMs
    return state.copy(screenOn = true, screenOffSince = null, stretchMs = if (tookBreak) 0 else state.stretchMs)
}
