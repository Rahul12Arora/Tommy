package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.BubbleTrigger
import java.time.Duration
import java.time.Instant

/** Bubble priority, highest first (bubble rules in DISCUSSION, §14.6). A session's end line is a focus line (D-115). */
enum class BubbleTier { FOCUS, STRETCH_NAG, LIMIT_NAG, BREAK, REWARD, IDLE }

val BubbleTrigger.tier: BubbleTier
    get() = when (this) {
        BubbleTrigger.FOCUS_DRIFT, BubbleTrigger.FOCUS_STARTED, BubbleTrigger.FOCUS_CHECKIN, BubbleTrigger.FOCUS_BROKEN,
        BubbleTrigger.FOCUS_COMPLETED,
        -> BubbleTier.FOCUS
        BubbleTrigger.STRETCH_REACHED -> BubbleTier.STRETCH_NAG
        BubbleTrigger.LIMIT_REACHED, BubbleTrigger.LIMIT_REPEAT -> BubbleTier.LIMIT_NAG
        BubbleTrigger.BREAK_DUE -> BubbleTier.BREAK
        BubbleTrigger.GOAL_MET, BubbleTrigger.BREAK_TAKEN, BubbleTrigger.PET_RETURNED ->
            BubbleTier.REWARD
        BubbleTrigger.IDLE -> BubbleTier.IDLE
    }

/** Nags and break reminders still reach the user while the pet isn't on screen, as notifications (D-24). */
private val BubbleTier.notifiesWhenHidden: Boolean
    get() = this == BubbleTier.STRETCH_NAG || this == BubbleTier.LIMIT_NAG || this == BubbleTier.BREAK

/**
 * A bubble someone wants shown. [values] fill the line's placeholders. A newer request with the same [key] replaces
 * a waiting one; M7 gives each limit its own key.
 */
data class BubbleRequest(
    val trigger: BubbleTrigger,
    val values: Map<String, String> = emptyMap(),
    val key: String = trigger.id,
)

/** What the arbiter remembers between ticks. */
data class ArbiterState(val pending: List<BubbleRequest> = emptyList(), val lastShownAt: Instant? = null) {
    /** Withdraws a waiting request that no longer applies, e.g. a nag after leaving the app (§14.7). */
    fun without(key: String): ArbiterState = copy(pending = pending.filterNot { it.key == key })
}

/** What the pet's situation allows this tick. [focusActive]: a session ran when the tick began (D-115). */
data class BubbleConditions(val petOnScreen: Boolean, val focusActive: Boolean, val minGap: Duration)

data class Arbitrated(val state: ArbiterState, val bubble: BubbleRequest?, val notifications: List<BubbleRequest>)

/**
 * Picks at most one bubble from the waiting and [offered] requests (A-21, D-82). The gap runs from when the previous
 * bubble appeared; only `focus_drift` ignores it. While a focus session runs only focus bubbles show and the rest
 * wait (D-25), except idle lines, which are dropped (D-26). While the pet isn't on screen, nags and break reminders
 * become notifications and everything else is dropped (D-24).
 */
fun arbitrate(state: ArbiterState, offered: List<BubbleRequest>, now: Instant, conditions: BubbleConditions): Arbitrated {
    val offeredKeys = offered.map { it.key }.toSet()
    var waiting = state.pending.filterNot { it.key in offeredKeys } + offered
    if (conditions.focusActive) waiting = waiting.filterNot { it.trigger.tier == BubbleTier.IDLE }
    val (eligible, held) = waiting.partition { !conditions.focusActive || it.trigger.tier == BubbleTier.FOCUS }

    if (!conditions.petOnScreen) {
        return Arbitrated(state.copy(pending = held), bubble = null, eligible.filter { it.trigger.tier.notifiesWhenHidden })
    }

    // Stable sort: within a tier, the request that has waited longest comes first
    val first = eligible.sortedBy { it.trigger.tier }.firstOrNull()
    val gapPassed = state.lastShownAt == null || !now.isBefore(state.lastShownAt.plus(conditions.minGap))
    if (first == null || !(gapPassed || first.trigger == BubbleTrigger.FOCUS_DRIFT)) {
        return Arbitrated(state.copy(pending = waiting), bubble = null, notifications = emptyList())
    }
    return Arbitrated(ArbiterState(pending = waiting - first, lastShownAt = now), first, notifications = emptyList())
}
