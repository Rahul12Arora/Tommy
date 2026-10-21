package com.companionstudio.nyx.config

/** The `{…}` names a line may contain (§14.6). */
object Placeholder {
    const val APP = "app"
    const val CATEGORY = "category"
    const val LIMIT = "limit"
    const val MINUTES = "minutes"
}

/**
 * The triggers that show a speech bubble, so every personality needs a line pool for each (§4, rule
 * `line-coverage`). `snoozed` and `doomscroll_10min` only move mood, so they aren't here.
 * [placeholders]: the ones its lines may use (rule `line-placeholders`); whoever offers the bubble fills them in.
 */
enum class BubbleTrigger(val id: String, val placeholders: Set<String> = emptySet()) {
    LIMIT_REACHED("limit_reached", setOf(Placeholder.APP, Placeholder.CATEGORY, Placeholder.LIMIT)),
    LIMIT_REPEAT("limit_repeat", setOf(Placeholder.APP, Placeholder.CATEGORY, Placeholder.LIMIT)),
    STRETCH_REACHED("stretch_reached", setOf(Placeholder.APP, Placeholder.MINUTES)),
    BREAK_DUE("break_due", setOf(Placeholder.MINUTES)),
    BREAK_TAKEN("break_taken"),
    GOAL_MET("goal_met"),
    FOCUS_STARTED("focus_started"),
    FOCUS_DRIFT("focus_drift", setOf(Placeholder.APP)),
    FOCUS_CHECKIN("focus_checkin", setOf(Placeholder.MINUTES)),
    FOCUS_COMPLETED("focus_completed"),
    FOCUS_BROKEN("focus_broken"),
    IDLE("idle"),
    PET_RETURNED("pet_returned"),
}

/** The pool every trigger must have; band-specific pools are optional. */
const val ANY_POOL = "any"
