package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.ObstructiveConfig
import com.companionstudio.nyx.tracking.EndedBreak

/** How Nyx reacts when a break is due (CR-10): a bubble and a notification, or the break screen. */
enum class BreakMode { GENTLE, OBSTRUCTIVE }

/**
 * SM-03 and the break screen since the last break (§14.7). Times are continuous screen use, not the clock, so a short
 * screen-off doesn't count towards "5 more minutes" or "come back in 60 s".
 * [nextDueMs]: the continuous use at which the next reminder is due; `null` = the break limit itself.
 * [screenSnoozes]: "5 more minutes" taps this sitting.
 */
data class BreakState(val nextDueMs: Long? = null, val screenSnoozes: Int = 0, val screenShowing: Boolean = false)

/** Everything the break rules need from config and the pet's situation this tick. */
data class BreakRules(
    val enabled: Boolean,
    val afterMs: Long,
    val repeatMs: Long,
    val mode: BreakMode,
    /** Pet on screen, not in a call, no focus session: the break screen may show (D-89). */
    val screenAllowed: Boolean,
)

enum class BreakAction {
    NONE,

    /** Gentle reminder: a `break_due` bubble and N-02 (US-11). */
    REMIND,

    /** Show the break screen (D-88). */
    SHOW_SCREEN,
}

/**
 * [taken]: a break ended after a reminder was due, so it counts (+5, D-97). [removeScreen]: close a break screen that's
 * showing (a break was taken, or it may no longer show, e.g. a call came in).
 */
data class BreakStep(val state: BreakState, val action: BreakAction, val taken: Boolean, val removeScreen: Boolean)

fun advanceBreaks(state: BreakState, continuousMs: Long, endedBreaks: List<EndedBreak>, rules: BreakRules): BreakStep {
    if (!rules.enabled) return BreakStep(BreakState(), BreakAction.NONE, taken = false, removeScreen = state.screenShowing)

    // Any break starts a new sitting; it only counts when the use before it had reached the limit (A-26, D-97)
    val taken = endedBreaks.any { it.continuousBeforeMs >= rules.afterMs }
    val current = if (endedBreaks.isEmpty()) state else BreakState()
    val removed = state.screenShowing && !current.screenShowing

    if (current.screenShowing) {
        if (rules.screenAllowed) return BreakStep(current, BreakAction.NONE, taken, removeScreen = false)
        // A call came in or the pet can't draw: close it, and don't remind again before the usual interval
        val closed = BreakState(nextDueMs = continuousMs + rules.repeatMs, screenSnoozes = current.screenSnoozes)
        return BreakStep(closed, BreakAction.NONE, taken, removeScreen = true)
    }
    if (continuousMs < (current.nextDueMs ?: rules.afterMs)) return BreakStep(current, BreakAction.NONE, taken, removed)
    if (rules.mode == BreakMode.OBSTRUCTIVE && rules.screenAllowed) {
        return BreakStep(current.copy(screenShowing = true), BreakAction.SHOW_SCREEN, taken, removed)
    }
    return BreakStep(current.copy(nextDueMs = continuousMs + rules.repeatMs), BreakAction.REMIND, taken, removed)
}

/** The answer on the break screen (D-88, D-98). A "5 more minutes" past the cap counts as "Take a break". */
fun answerBreakScreen(
    state: BreakState,
    takeBreak: Boolean,
    continuousMs: Long,
    obstructive: ObstructiveConfig,
): BreakState {
    if (!state.screenShowing) return state
    val snooze = !takeBreak && state.screenSnoozes < obstructive.maxSnoozes
    return if (snooze) {
        BreakState(continuousMs + obstructive.snoozeMinutes * 60_000L, state.screenSnoozes + 1, screenShowing = false)
    } else {
        BreakState(continuousMs + obstructive.returnAfterTakeBreakSeconds * 1000L, state.screenSnoozes, screenShowing = false)
    }
}

/** Whether "5 more minutes" is still on offer this sitting. */
fun canSnoozeBreakScreen(state: BreakState, maxSnoozes: Int): Boolean = state.screenSnoozes < maxSnoozes
