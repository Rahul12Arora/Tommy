package com.companionstudio.nyx.config

/**
 * The triggers that show a speech bubble, so every personality needs a line pool for each (§4, rule
 * `line-coverage`). `snoozed` and `doomscroll_10min` only move mood, so they aren't here.
 */
enum class BubbleTrigger(val id: String) {
    LIMIT_REACHED("limit_reached"),
    LIMIT_REPEAT("limit_repeat"),
    STRETCH_REACHED("stretch_reached"),
    BREAK_DUE("break_due"),
    BREAK_TAKEN("break_taken"),
    GOAL_MET("goal_met"),
    FOCUS_STARTED("focus_started"),
    FOCUS_DRIFT("focus_drift"),
    FOCUS_CHECKIN("focus_checkin"),
    FOCUS_COMPLETED("focus_completed"),
    FOCUS_BROKEN("focus_broken"),
    IDLE("idle"),
    PET_RETURNED("pet_returned"),
}

/** The pool every trigger must have; band-specific pools are optional. */
const val ANY_POOL = "any"
