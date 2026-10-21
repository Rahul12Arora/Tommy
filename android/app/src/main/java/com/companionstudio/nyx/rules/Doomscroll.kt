package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.Category

private const val DOOMSCROLL_STEP_MS = 10 * 60_000L

/**
 * How many `doomscroll_10min` events today's log is missing (D-81): one per full 10 minutes in the doomscroll
 * categories, minus the [logged] ones. Worked out from the stored totals, so a restart neither loses nor repeats one.
 */
fun doomscrollEventsDue(todayMs: Map<Category, Long>, doomscrollCategories: List<Category>, logged: Int): Int {
    val doomscrollMs = doomscrollCategories.toSet().sumOf { todayMs[it] ?: 0L }
    return (doomscrollMs / DOOMSCROLL_STEP_MS - logged).coerceAtLeast(0).toInt()
}
