package com.companionstudio.nyx.focus

import java.time.Instant

/** SM-04's live states; the end states are [FocusOutcome]s. Kept in memory, re-derived after a restart (§14.8). */
enum class FocusPhase { RUNNING, DRIFTING, PAUSED }

/** What a session step needs from the tick. */
data class FocusInput(
    /** Time credited at this tick per counted app (D-110). */
    val creditedMs: Map<String, Long>,
    /** The counted app in front; `null` for the launcher, the shade, Nyx or nothing (A-02). */
    val frontPackage: String?,
    val screenOn: Boolean,
    val inCall: Boolean,
    val now: Instant,
)

/** [checkinMs]: `null` when the session doesn't check in. */
data class FocusRules(val abandonAfterMs: Long, val checkinMs: Long?)

/**
 * The session after a tick. [session] carries the new focus time, switches and last focus time; [outcome] non-null =
 * it ends now. [driftedTo]: a switch started at this tick, to that app. [checkinAtMinutes]: a check-in is due, at that
 * many focus minutes.
 */
data class FocusStep(
    val session: FocusSession,
    val phase: FocusPhase,
    val checkinIndex: Int,
    val outcome: FocusOutcome? = null,
    val driftedTo: String? = null,
    val checkinAtMinutes: Int? = null,
)

/**
 * One SM-04 step (D-110, D-111, D-114). Focus time is what the tracker credited to the allowed apps, so it stops by
 * itself with the screen off or locked and outside those apps. A switch is a counted app that isn't allowed, in front
 * or credited since the last tick, outside a call; the launcher, the shade, the ring and Nyx are never counted apps.
 * The timer wins over a switch in the same tick.
 */
fun stepFocus(session: FocusSession, phase: FocusPhase, checkinIndex: Int, input: FocusInput, rules: FocusRules): FocusStep {
    val allowed = session.allowedApps.toSet()
    val gainedMs = input.creditedMs.filterKeys { it in allowed }.values.sum()
    val focusMs = session.focusMs + gainedMs
    val lastFocusAtMs = if (gainedMs > 0) input.now.toEpochMilli() else session.lastFocusAtMs

    // The app in front first, so a line names where the user is now
    val stray = if (input.inCall) null else (listOfNotNull(input.frontPackage) + input.creditedMs.keys).firstOrNull { it !in allowed }
    val switched = stray != null && phase != FocusPhase.DRIFTING
    val newPhase = when {
        !input.screenOn -> FocusPhase.PAUSED
        input.frontPackage != null && input.frontPackage in allowed -> FocusPhase.RUNNING
        stray != null -> FocusPhase.DRIFTING
        phase == FocusPhase.PAUSED -> FocusPhase.RUNNING
        // The launcher or the shade on the way back doesn't end a drift
        else -> phase
    }
    val updated = session.copy(
        focusMs = focusMs,
        switches = session.switches + if (switched) 1 else 0,
        lastFocusAtMs = lastFocusAtMs,
    )

    val goalMs = session.goalMinutes?.let { it * 60_000L }
    val outcome = when {
        session.goal != FocusGoal.UNTIL_DONE && goalMs != null && focusMs >= goalMs -> FocusOutcome.COMPLETED
        switched && session.goal == FocusGoal.NO_SWITCHING -> FocusOutcome.BROKEN
        input.now.toEpochMilli() - lastFocusAtMs >= rules.abandonAfterMs -> FocusOutcome.ENDED_EARLY
        else -> null
    }
    if (outcome != null) return FocusStep(updated, newPhase, checkinIndex, outcome)

    val newIndex = checkinIndexAt(focusMs, rules.checkinMs)
    return FocusStep(
        updated,
        newPhase,
        newIndex,
        driftedTo = stray.takeIf { switched },
        checkinAtMinutes = if (newIndex > checkinIndex) (focusMs / 60_000).toInt() else null,
    )
}

/** Check-ins come every interval of focus time; after a restart the index starts here, so none fires at once. */
fun checkinIndexAt(focusMs: Long, checkinMs: Long?): Int = if (checkinMs == null) 0 else (focusMs / checkinMs).toInt()

/** Done counts only on an until-done session with enough focus time (D-113); otherwise only End is offered. */
fun canFinishAsDone(session: FocusSession, minDoneMinutes: Int): Boolean =
    session.goal == FocusGoal.UNTIL_DONE && session.focusMs >= minDoneMinutes * 60_000L
