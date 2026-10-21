package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.BubbleTrigger
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.data.RETENTION_DAYS
import com.companionstudio.nyx.mood.bandAt
import com.companionstudio.nyx.mood.moodOf
import com.companionstudio.nyx.mood.presenceChange
import com.companionstudio.nyx.tracking.TrackerSnapshot
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlin.random.Random

/** What the pet does after a tick: its mood, and the line to show in a bubble, if any. */
data class Reaction(val mood: Int, val bubble: String?)

/**
 * The engine's step after the tracker (§14.6): logs what happened, recomputes mood, notices the pet running away
 * and coming back, and lets the arbiter pick at most one bubble. One instance per engine run, used only from the
 * engine coroutine; the arbiter's state and the recent lines live here, in memory.
 */
class Reactions(private val dao: PetEventDao, private val clock: Clock, random: Random = Random.Default) {
    private val lines = LinePicker(random)
    private var arbiter = ArbiterState()
    private var cleanedUpDay: LocalDate? = null

    /** [personalityId]: the user's pick, `null` before they pick. [petOnScreen]: shown and allowed to draw. */
    suspend fun afterTick(
        snapshot: TrackerSnapshot,
        config: EffectiveConfig,
        personalityId: String?,
        petOnScreen: Boolean,
    ): Reaction {
        val document = config.document
        val now = clock.instant()
        val day = snapshot.day.toString()
        if (snapshot.day != cleanedUpDay) {
            dao.deleteBefore(snapshot.day.minusDays(RETENTION_DAYS).toString())
            cleanedUpDay = snapshot.day
        }

        val logged = dao.eventsOn(day).map { it.type }
        val due = doomscrollEventsDue(
            snapshot.todayMs,
            document.tracking.doomscrollCategories,
            logged = logged.count { it == PetEventType.DOOMSCROLL_10MIN },
        )
        val added = MutableList(due) { PetEventType.DOOMSCROLL_10MIN }
        val mood = moodOf(logged + added, document.mood)
        presenceChange(logged + added, mood)?.let { added += it }
        if (added.isNotEmpty()) dao.insertAll(added.map { PetEvent(day = day, atMs = now.toEpochMilli(), type = it) })

        val offered = if (PetEventType.PET_RETURNED in added) listOf(BubbleRequest(BubbleTrigger.PET_RETURNED)) else emptyList()
        val conditions = BubbleConditions(
            petOnScreen = petOnScreen,
            focusActive = false, // focus sessions arrive in M8
            minGap = Duration.ofSeconds(document.bubbles.minGapSeconds.toLong()),
        )
        val result = arbitrate(arbiter, offered, now, conditions)
        arbiter = result.state
        // Nothing in M6 offers a nag or a break reminder, so there are no notifications to post yet (N-02, N-04: M7)

        val bubble = result.bubble?.let { request ->
            val personality = document.personalities.firstOrNull { it.id == personalityId && it.enabled }
                ?: document.personalities.first { it.enabled }
            lines.pick(linePool(document, personality.id, request.trigger, document.mood.bandAt(mood).id), request)
        }
        return Reaction(mood, bubble)
    }
}
