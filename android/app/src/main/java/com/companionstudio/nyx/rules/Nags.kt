package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.BubbleTrigger
import java.time.Duration
import java.time.Instant

/** SM-02 per limit (§14.7). Glowing, Bubble and Waiting are one phase here: the bubble is just a request. */
enum class NagPhase { UNDER, NAGGING, PAUSED, SNOOZED }

/** [lastLineAt]: when this limit last offered a line; repeats count from it (D-94). */
data class NagState(val phase: NagPhase = NagPhase.UNDER, val lastLineAt: Instant? = null, val snoozedUntil: Instant? = null)

/** A line a limit wants to say this tick. */
data class NagLine(val check: LimitCheck, val trigger: BubbleTrigger)

/** [exceeded]: keys crossed for the first time today, to log as `LIMIT_EXCEEDED` (once per key per day, D-93). */
data class NagStep(val states: Map<String, NagState>, val exceeded: List<String>, val lines: List<NagLine>) {
    /** The pet glows while any limit is nagging; not while paused or snoozed (SM-02). */
    val glowing: Boolean get() = states.values.any { it.phase == NagPhase.NAGGING }
}

/**
 * States at the first tick of an engine run or a day (D-93): a limit that's over and already logged today starts
 * paused, as if it had just said its line, so a restart neither costs mood again nor nags at once.
 */
fun rebuildNags(checks: List<LimitCheck>, exceededToday: Set<String>, now: Instant): Map<String, NagState> =
    checks.associate { check ->
        val alreadyNagged = check.over && check.key in exceededToday
        check.key to if (alreadyNagged) NagState(NagPhase.PAUSED, lastLineAt = now) else NagState()
    }

/** One tick of every limit's nag. Limits that were turned off since the last tick are dropped. */
fun advanceNags(
    states: Map<String, NagState>,
    checks: List<LimitCheck>,
    exceededToday: Set<String>,
    now: Instant,
    repeat: Duration,
): NagStep {
    val exceeded = mutableListOf<String>()
    val lines = mutableListOf<NagLine>()
    val next = checks.associate { check ->
        val state = states[check.key] ?: NagState()
        val repeatDue = state.lastLineAt == null || !now.isBefore(state.lastLineAt.plus(repeat))
        val newState = when {
            // Back under: a break reset the stretch, or the user raised the limit
            !check.over -> NagState()
            state.phase == NagPhase.UNDER -> if (check.covered) {
                if (check.key !in exceededToday) exceeded += check.key
                lines += NagLine(check, check.firstTrigger)
                NagState(NagPhase.NAGGING, lastLineAt = now)
            } else {
                state
            }
            state.phase == NagPhase.SNOOZED && state.snoozedUntil?.let(now::isBefore) == true -> state
            !check.covered -> state.copy(phase = NagPhase.PAUSED, snoozedUntil = null)
            // Nagging, paused or a snooze that just ended, in a covered app: glow now, repeat on schedule (D-94)
            repeatDue || state.phase == NagPhase.SNOOZED -> {
                lines += NagLine(check, check.repeatTrigger)
                NagState(NagPhase.NAGGING, lastLineAt = now)
            }
            else -> state.copy(phase = NagPhase.NAGGING)
        }
        check.key to newState
    }
    return NagStep(next, exceeded, lines)
}

/** Silences [key] until [now] + [snooze] (D-28, D-95); `null` when that limit isn't nagging or paused. */
fun snoozeNag(states: Map<String, NagState>, key: String, now: Instant, snooze: Duration): Map<String, NagState>? {
    val state = states[key] ?: return null
    if (state.phase != NagPhase.NAGGING && state.phase != NagPhase.PAUSED) return null
    return states + (key to state.copy(phase = NagPhase.SNOOZED, snoozedUntil = now.plus(snooze)))
}
