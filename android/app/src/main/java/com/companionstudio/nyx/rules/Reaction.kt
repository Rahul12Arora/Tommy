package com.companionstudio.nyx.rules

import com.companionstudio.nyx.focus.FocusStatus

/** A line to show beside the pet. [snoozeKey]: the limit its Snooze button silences; `null` = no Snooze (US-10). */
data class ShownBubble(val line: String, val snoozeKey: String? = null)

/** What the break screen (O-04) should do after a tick. */
sealed interface BreakScreenChange {
    /** [canSnooze]: "5 more minutes" is still on offer. [buttonDelaySeconds]: real seconds before the buttons work. */
    data class Show(val line: String, val continuousMs: Long, val canSnooze: Boolean, val buttonDelaySeconds: Int) : BreakScreenChange

    data object Remove : BreakScreenChange
}

/**
 * What the actions ring and the pet show that only the engine knows (§14.7, §14.8). `null` limits are off; [focus] is
 * `null` when no session runs. [frontPackage]: the counted app in front, which S-09 selects when opened from the ring.
 */
data class RingStatus(
    val continuousMs: Long = 0,
    val breakAfterMinutes: Int? = null,
    val stretchMs: Long = 0,
    val stretchLimitMinutes: Int? = null,
    val nags: Map<String, NagState> = emptyMap(),
    val focus: FocusStatus? = null,
    val frontPackage: String? = null,
)

/**
 * What the pet does after a tick. [nagNotifications] become N-04 (pet not on screen), [breakNotification] N-02;
 * [breakTaken] cancels N-02. [focusRunning]: a session runs and the screen is on, so ticks are fast (§14.8).
 */
data class Reaction(
    val mood: Int,
    val bubble: ShownBubble? = null,
    val glowing: Boolean = false,
    val nagNotifications: List<String> = emptyList(),
    val breakNotification: String? = null,
    val breakTaken: Boolean = false,
    val breakScreen: BreakScreenChange? = null,
    val status: RingStatus = RingStatus(),
    val focusRunning: Boolean = false,
)
