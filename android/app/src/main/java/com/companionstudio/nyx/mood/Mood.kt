package com.companionstudio.nyx.mood

import com.companionstudio.nyx.config.MoodBand
import com.companionstudio.nyx.config.MoodConfig
import com.companionstudio.nyx.config.MoodWeights
import com.companionstudio.nyx.rules.PetEventType

/** Mood is always within this range; 0 means the pet has run away (SM-01). */
val MOOD_RANGE = 0..100

/**
 * Today's mood (US-15, D-79): [mood]'s start value, then each event's weight in log order, clamped after every
 * event. Clamping once at the end instead would let a pet sink far below 0, and a break couldn't bring it back (US-16).
 */
fun moodOf(events: List<PetEventType>, mood: MoodConfig): Int =
    events.fold(mood.start) { value, event -> (value + weightOf(event, mood.weights)).coerceIn(MOOD_RANGE) }

fun weightOf(event: PetEventType, weights: MoodWeights): Int = when (event) {
    PetEventType.DOOMSCROLL_10MIN -> weights.doomscroll10Min
    PetEventType.LIMIT_EXCEEDED -> weights.limitExceeded
    PetEventType.SNOOZED -> weights.snoozed
    PetEventType.BREAK_TAKEN -> weights.breakTaken
    PetEventType.GOAL_MET -> weights.goalMet
    PetEventType.FOCUS_COMPLETED -> weights.focusCompleted
    PetEventType.RAN_AWAY, PetEventType.PET_RETURNED -> 0
}

/** The band holding [value]. The validator guarantees the bands cover 0–100 without gaps (§14.2). */
fun MoodConfig.bandAt(value: Int): MoodBand = bands.first { value in it.from..it.to }

/**
 * What the log should say next about the pet being away (US-16, D-80): [PetEventType.RAN_AWAY] when [mood] has
 * reached 0, [PetEventType.PET_RETURNED] when it's above 0 again after that, otherwise `null`.
 */
fun presenceChange(events: List<PetEventType>, mood: Int): PetEventType? {
    val away = events.lastOrNull { it == PetEventType.RAN_AWAY || it == PetEventType.PET_RETURNED } == PetEventType.RAN_AWAY
    return when {
        mood == 0 && !away -> PetEventType.RAN_AWAY
        mood > 0 && away -> PetEventType.PET_RETURNED
        else -> null
    }
}
