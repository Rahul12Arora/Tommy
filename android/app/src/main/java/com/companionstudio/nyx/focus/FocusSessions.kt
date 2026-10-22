package com.companionstudio.nyx.focus

import com.companionstudio.nyx.config.BubbleTrigger
import com.companionstudio.nyx.config.FocusConfig
import com.companionstudio.nyx.config.Placeholder
import com.companionstudio.nyx.rules.BubbleRequest
import com.companionstudio.nyx.rules.CallState
import com.companionstudio.nyx.rules.NagLabels
import com.companionstudio.nyx.tracking.TrackerSnapshot
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * What the ring, the badge, the pet's description and the Home card show about the running session (D-118). Home has
 * no live phase (the engine keeps it in memory), so it shows [FocusPhase.RUNNING].
 */
data class FocusStatus(
    val goal: FocusGoal,
    val goalMinutes: Int?,
    val focusMs: Long,
    val phase: FocusPhase,
    val switches: Int,
    val canDone: Boolean,
    val minDoneMinutes: Int,
)

fun focusStatus(session: FocusSession, focus: FocusConfig, phase: FocusPhase = FocusPhase.RUNNING) = FocusStatus(
    goal = session.goal,
    goalMinutes = session.goalMinutes,
    focusMs = session.focusMs,
    phase = phase,
    switches = session.switches,
    canDone = canFinishAsDone(session, focus.minDoneMinutes),
    minDoneMinutes = focus.minDoneMinutes,
)

/**
 * The focus part of a tick. [held]: a session was running when the tick began, so only focus bubbles show — including
 * on the tick that ends it, so its end line comes first (D-115). [endedNow]: the session the engine knew has ended,
 * by the timer, a switch, the ring, Home or being abandoned. [status] is `null` when none runs after the tick.
 */
data class FocusTick(
    val held: Boolean = false,
    val endedNow: Boolean = false,
    val offers: List<BubbleRequest> = emptyList(),
    val status: FocusStatus? = null,
)

/**
 * Focus sessions as the engine sees them (§14.8). One instance per engine run, used only from the engine coroutine,
 * like `Reactions`. The session itself is in storage; this remembers the live phase and the check-ins, and notices
 * starts and ends whoever made them (D-109). A session that started before this run began is picked up silently.
 */
class FocusSessions(private val dao: FocusSessionDao, private val clock: Clock) {
    private val runStartedAt: Instant = clock.instant()
    private var knownId: Long? = null
    private var phase = FocusPhase.RUNNING
    private var checkinIndex = 0

    suspend fun step(snapshot: TrackerSnapshot, focus: FocusConfig, calls: CallState, labels: NagLabels): FocusTick {
        val now = clock.instant()
        val wasKnown = knownId
        val active = dao.active()
        val offers = mutableListOf<BubbleRequest>()

        var endedNow = false
        if (wasKnown != null && active?.id != wasKnown) {
            endedNow = true
            knownId = null
            dao.byId(wasKnown)?.outcome?.let { offers += endLines(it) }
        }
        if (active == null) return FocusTick(held = wasKnown != null, endedNow, offers)

        if (active.id != knownId) {
            // The first tick that sees a session only sets it up: its credits are from before the start (D-110)
            knownId = active.id
            phase = FocusPhase.RUNNING
            checkinIndex = checkinIndexAt(active.focusMs, checkinMs(active))
            if (!Instant.ofEpochMilli(active.startedAtMs).isBefore(runStartedAt)) offers += BubbleRequest(BubbleTrigger.FOCUS_STARTED)
            return FocusTick(held = true, endedNow, offers, statusOf(active, focus))
        }

        val input = FocusInput(snapshot.creditedMs, snapshot.frontPackage, snapshot.screenOn, calls.inCall(), now)
        val step = stepFocus(active, phase, checkinIndex, input, FocusRules(focus.abandonAfterMinutes * 60_000L, checkinMs(active)))
        phase = step.phase
        checkinIndex = step.checkinIndex
        // Saved before finishing, so an ended session keeps its last focus time and switches
        if (step.session != active) dao.saveProgress(active.id, step.session.focusMs, step.session.switches, step.session.lastFocusAtMs)
        val outcome = step.outcome
        if (outcome != null && dao.finish(active.id, outcome, now.toEpochMilli(), snapshot.day.toString())) {
            knownId = null
            return FocusTick(held = true, endedNow = true, offers + endLines(outcome))
        }

        // Quiet presence never speaks during the session; only check-in mode checks in (D-112)
        step.driftedTo?.takeIf { active.petMode != PetMode.QUIET }?.let {
            offers += BubbleRequest(BubbleTrigger.FOCUS_DRIFT, mapOf(Placeholder.APP to labels.app(it)))
        }
        step.checkinAtMinutes?.let {
            offers += BubbleRequest(BubbleTrigger.FOCUS_CHECKIN, mapOf(Placeholder.MINUTES to it.toString()))
        }
        return FocusTick(held = true, endedNow, offers, statusOf(step.session, focus))
    }

    /** Done or End on the ring (D-118). `false` when there's nothing to end, or Done isn't allowed (yet). */
    suspend fun end(done: Boolean, focus: FocusConfig, day: LocalDate): Boolean {
        val active = dao.active() ?: return false
        if (done && !canFinishAsDone(active, focus.minDoneMinutes)) return false
        val outcome = if (done) FocusOutcome.COMPLETED else FocusOutcome.ENDED_EARLY
        return dao.finish(active.id, outcome, clock.instant().toEpochMilli(), day.toString())
    }

    /** Retention (A-13), with the event log's cut-off day. */
    suspend fun deleteBefore(day: LocalDate) = dao.deleteBefore(day.toString())

    private fun statusOf(session: FocusSession, focus: FocusConfig) = focusStatus(session, focus, phase)

    private companion object {
        fun checkinMs(session: FocusSession): Long? =
            session.checkinMinutes?.takeIf { session.petMode == PetMode.CHECK_IN }?.let { it * 60_000L }

        /** Ended early says nothing (US-14). */
        fun endLines(outcome: FocusOutcome): List<BubbleRequest> = when (outcome) {
            FocusOutcome.COMPLETED -> listOf(BubbleRequest(BubbleTrigger.FOCUS_COMPLETED))
            FocusOutcome.BROKEN -> listOf(BubbleRequest(BubbleTrigger.FOCUS_BROKEN))
            FocusOutcome.ENDED_EARLY -> emptyList()
        }
    }
}
