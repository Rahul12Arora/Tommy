package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.BubbleTrigger
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.NyxConfig
import com.companionstudio.nyx.config.Placeholder
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.data.RETENTION_DAYS
import com.companionstudio.nyx.focus.FocusSessions
import com.companionstudio.nyx.mood.bandAt
import com.companionstudio.nyx.mood.moodOf
import com.companionstudio.nyx.mood.presenceChange
import com.companionstudio.nyx.tracking.TrackerSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/**
 * The engine's step after the tracker (§14.6, §14.7, §14.8): runs the focus session, logs what happened, recomputes
 * mood, runs the nags, breaks and idle lines, and lets the arbiter pick at most one bubble. One instance per engine
 * run, used only from the engine coroutine; everything it remembers between ticks lives here (or in [focus]), in
 * memory, and is rebuilt after a restart.
 */
class Reactions(
    private val dao: PetEventDao,
    private val clock: Clock,
    private val labels: NagLabels,
    private val focus: FocusSessions,
    random: Random = Random.Default,
) {
    private val lines = LinePicker(random)
    private var arbiter = ArbiterState()
    private var cleanedUpDay: LocalDate? = null
    private var nags = emptyMap<String, NagState>()
    private var nagDay: LocalDate? = null
    private var breaks = BreakState()
    private var idleBaselineMs: Long? = null

    // The last tick, for taps that arrive between ticks
    private var lastDay: LocalDate? = null
    private var lastContinuousMs = 0L
    private var lastTickAt: Instant? = null

    /** [personalityId]: the user's pick, `null` before they pick. [petOnScreen]: shown and allowed to draw. */
    suspend fun afterTick(
        snapshot: TrackerSnapshot,
        config: EffectiveConfig,
        personalityId: String?,
        petOnScreen: Boolean,
        prefs: ReactionPrefs = ReactionPrefs(),
        calls: CallState = CallState { false },
    ): Reaction {
        val document = config.document
        val now = clock.instant()
        val day = snapshot.day.toString()
        if (snapshot.day != cleanedUpDay) {
            dao.deleteBefore(snapshot.day.minusDays(RETENTION_DAYS).toString())
            focus.deleteBefore(snapshot.day.minusDays(RETENTION_DAYS))
            cleanedUpDay = snapshot.day
        }

        // Before reading the log: a session that completes now logs its FOCUS_COMPLETED (D-109). Not gated by
        // features.focus: switching it off hides Start, but a running session carries on (D-120)
        val focusTick = focus.step(snapshot, document.focus, calls, labels)

        val logged = dao.eventsOn(day)
        val added = mutableListOf<PetEvent>()
        fun log(type: PetEventType, subject: String? = null) {
            added += PetEvent(day = day, atMs = now.toEpochMilli(), type = type, subject = subject)
        }
        val due = doomscrollEventsDue(
            snapshot.todayMs,
            document.tracking.doomscrollCategories,
            logged = logged.count { it.type == PetEventType.DOOMSCROLL_10MIN },
        )
        repeat(due) { log(PetEventType.DOOMSCROLL_10MIN) }

        val nagStep = stepNags(snapshot, config, logged, now)
        nagStep.exceeded.forEach { log(PetEventType.LIMIT_EXCEEDED, subject = it) }

        val mode = effectiveBreakMode(prefs, document.breaks.obstructive.allowed)
        val breakStep = advanceBreaks(
            breaks,
            snapshot.continuousMs,
            snapshot.endedBreaks,
            BreakRules(
                enabled = document.features.breaks,
                afterMs = checkNotNull(config.valueOf(SettingPaths.BREAK_AFTER_MINUTES)) * 60_000L,
                repeatMs = document.breaks.repeatMinutes * 60_000L,
                mode = mode,
                // A focus session holds the screen too (D-25, D-89)
                screenAllowed = !focusTick.held && petOnScreen && mode == BreakMode.OBSTRUCTIVE && !calls.inCall(),
            ),
        )
        breaks = breakStep.state
        if (breakStep.taken) log(PetEventType.BREAK_TAKEN)
        // Reminders that fell due during a session aren't offered; when it ends, the schedule starts over, so one
        // that's still due fires at the next tick in the user's mode (D-115)
        val remind = breakStep.action == BreakAction.REMIND && !focusTick.held
        if (focusTick.endedNow) breaks = breaks.copy(nextDueMs = null)

        val types = logged.map { it.type } + added.map { it.type }
        val mood = moodOf(types, document.mood)
        presenceChange(types, mood)?.let { log(it) }
        if (added.isNotEmpty()) dao.insertAll(added)

        val breakDue = BubbleRequest(
            BubbleTrigger.BREAK_DUE,
            mapOf(Placeholder.MINUTES to document.breaks.breakMinutes.toString()),
            key = BREAK_KEY,
        )
        val offered = buildList {
            addAll(focusTick.offers)
            nagStep.lines.forEach { add(nagRequest(it.check, it.trigger, snapshot.frontPackage, labels)) }
            if (remind) add(breakDue)
            // A pet coming back says so, unless a finished session brought it back: one line for one moment
            val returned = added.any { it.type == PetEventType.PET_RETURNED }
            val completed = focusTick.offers.any { it.trigger == BubbleTrigger.FOCUS_COMPLETED }
            if (returned && !completed) add(BubbleRequest(BubbleTrigger.PET_RETURNED))
            if (breakStep.taken && !returned) add(BubbleRequest(BubbleTrigger.BREAK_TAKEN))
        }.toMutableList()
        val totalMs = snapshot.todayMs.values.sum()
        val idle = idleStep(
            idleBaselineMs,
            totalMs,
            everyMs = document.idle.everyMinutes * 60_000L,
            enabled = document.features.idleLines && prefs.idleEnabled,
        )
        if (idle.offer) offered += BubbleRequest(BubbleTrigger.IDLE)

        // A nag still waiting for its turn is withdrawn once its limit stops nagging; a reminder once the break is taken
        arbiter = arbiter.pending.map { it.key }
            .filter { key ->
                (key.isLimitKey() && nags[key]?.phase != NagPhase.NAGGING) || (key == BREAK_KEY && (breakStep.taken || focusTick.endedNow))
            }
            .fold(arbiter) { state, key -> state.without(key) }
        val result = arbitrate(
            arbiter,
            offered,
            now,
            BubbleConditions(
                petOnScreen = petOnScreen,
                focusActive = focusTick.held,
                minGap = Duration.ofSeconds(document.bubbles.minGapSeconds.toLong()),
            ),
        )
        // An idle line is for when the pet has been quiet: once anything else is said, a waiting one goes (BUG-03)
        arbiter = if (result.bubble != null) result.state.without(BubbleTrigger.IDLE.id) else result.state
        idleBaselineMs = if (result.bubble != null) totalMs else idle.baselineMs

        val band = document.mood.bandAt(mood).id
        val personality = personalityOf(document, personalityId)
        fun say(request: BubbleRequest) = lines.pick(linePool(document, personality, request.trigger, band), request)

        val snoozesLeft = types.count { it == PetEventType.SNOOZED } < document.nags.maxSnoozesPerDay
        val obstructive = document.breaks.obstructive
        lastDay = snapshot.day
        lastContinuousMs = snapshot.continuousMs
        lastTickAt = now
        return Reaction(
            mood = mood,
            bubble = result.bubble?.let { ShownBubble(say(it), snoozeKey = it.key.takeIf { key -> key.isLimitKey() && snoozesLeft }) },
            // The glow is part of a nag, so it waits for the session too (D-115)
            glowing = nagStep.glowing && !focusTick.held,
            // Break reminders already post N-02 below, pet on screen or not (US-11)
            nagNotifications = result.notifications.filter { it.key.isLimitKey() }.map(::say),
            breakNotification = if (remind) say(breakDue) else null,
            breakTaken = breakStep.taken,
            breakScreen = when {
                breakStep.action == BreakAction.SHOW_SCREEN -> BreakScreenChange.Show(
                    say(breakDue),
                    snapshot.continuousMs,
                    canSnoozeBreakScreen(breaks, obstructive.maxSnoozes),
                    obstructive.buttonDelaySeconds,
                )
                breakStep.removeScreen -> BreakScreenChange.Remove
                else -> null
            },
            status = RingStatus(
                continuousMs = snapshot.continuousMs,
                breakAfterMinutes = if (document.features.breaks) config.valueOf(SettingPaths.BREAK_AFTER_MINUTES) else null,
                stretchMs = snapshot.stretchMs,
                stretchLimitMinutes = config.valueOf(SettingPaths.STRETCH_MINUTES)?.takeIf { it > 0 },
                nags = nags,
                focus = focusTick.status,
                frontPackage = snapshot.frontPackage,
            ),
            focusRunning = focusTick.status != null && snapshot.screenOn,
        )
    }

    /**
     * Snooze on a nag's bubble (US-10, D-95): silences only [key], costs mood, and counts towards today's maximum.
     * Returns `false` when it can't be snoozed (the maximum is used up, or the limit stopped nagging meanwhile).
     */
    suspend fun snooze(key: String, config: EffectiveConfig): Boolean {
        val day = lastDay?.toString() ?: return false
        val snoozedToday = dao.eventsOn(day).count { it.type == PetEventType.SNOOZED }
        if (snoozedToday >= config.document.nags.maxSnoozesPerDay) return false
        val now = clock.instant()
        val snoozeMinutes = checkNotNull(config.valueOf(SettingPaths.SNOOZE_MINUTES)).toLong()
        nags = snoozeNag(nags, key, now, Duration.ofMinutes(snoozeMinutes)) ?: return false
        arbiter = arbiter.without(key)
        dao.insertAll(listOf(PetEvent(day = day, atMs = now.toEpochMilli(), type = PetEventType.SNOOZED, subject = key)))
        return true
    }

    /** The user's answer on the break screen (D-88, D-98); the screen itself has already closed. */
    fun answerBreakScreen(takeBreak: Boolean, config: EffectiveConfig) {
        // The answer comes between ticks: the screen was on all along, so add the time since the last one
        val sinceTick = lastTickAt?.let { Duration.between(it, clock.instant()).toMillis().coerceAtLeast(0) } ?: 0
        breaks = answerBreakScreen(breaks, takeBreak, lastContinuousMs + sinceTick, config.document.breaks.obstructive)
    }

    /** Done or End on the ring's focus card (D-118). `false` when nothing could be ended. */
    suspend fun endFocus(done: Boolean, config: EffectiveConfig): Boolean {
        val day = lastDay ?: return false
        return focus.end(done, config.document.focus, day)
    }

    private fun stepNags(snapshot: TrackerSnapshot, config: EffectiveConfig, logged: List<PetEvent>, now: Instant): NagStep {
        val checks = limitChecks(snapshot, config)
        val exceededToday = logged.filter { it.type == PetEventType.LIMIT_EXCEEDED }.mapNotNull { it.subject }.toSet()
        if (snapshot.day != nagDay) {
            nags = rebuildNags(checks, exceededToday, now)
            nagDay = snapshot.day
        }
        val repeat = Duration.ofMinutes(checkNotNull(config.valueOf(SettingPaths.NAG_REPEAT_MINUTES)).toLong())
        return advanceNags(nags, checks, exceededToday, now, repeat).also { nags = it.states }
    }

    private companion object {
        const val BREAK_KEY = "break"

        fun String.isLimitKey() = startsWith("limit:") || this == LimitKeys.STRETCH

        /** The user's personality if it's still enabled, otherwise the first enabled one (§14.6). */
        fun personalityOf(document: NyxConfig, personalityId: String?): String =
            (document.personalities.firstOrNull { it.id == personalityId && it.enabled } ?: document.personalities.first { it.enabled }).id
    }
}
