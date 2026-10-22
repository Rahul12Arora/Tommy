package com.companionstudio.nyx.tracking

import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths

/**
 * Seconds until the next tick (H-01). Fast when a limit that covers the front app, or the break limit, is less than one
 * slow tick away, so the crossing is seen within a fast tick; and while the pet [glowing], so the glow stops within a
 * fast tick of leaving the app (D-107); and while a focus session runs with the screen on, so a switch and the timer
 * are seen within a fast tick (§14.8).
 */
fun tickDelaySeconds(snapshot: TrackerSnapshot, config: EffectiveConfig, glowing: Boolean = false, focusRunning: Boolean = false): Int {
    val tracking = config.document.tracking
    val slowMs = tracking.slowTickSeconds * 1000L
    if (glowing || focusRunning) return tracking.fastTickSeconds
    val breakLimit = if (config.document.features.breaks) config.valueOf(SettingPaths.BREAK_AFTER_MINUTES) else null
    if (remainingMs(breakLimit, snapshot.continuousMs)?.let { it in 1..slowMs } == true) return tracking.fastTickSeconds
    val category = snapshot.frontCategory ?: return tracking.slowTickSeconds
    val stretchCovers = category in config.document.limits.stretch.categories
    val remainingMs = listOfNotNull(
        remainingMs(config.valueOf(SettingPaths.categoryDailyMinutes(category)), snapshot.todayMs[category] ?: 0),
        remainingMs(config.valueOf(SettingPaths.TOTAL_DAILY_MINUTES), snapshot.todayMs.values.sum()),
        if (stretchCovers) remainingMs(config.valueOf(SettingPaths.STRETCH_MINUTES), snapshot.stretchMs) else null,
    )
    return if (remainingMs.any { it in 1..slowMs }) tracking.fastTickSeconds else tracking.slowTickSeconds
}

/** `null` when there's no such limit or it's off (0). Negative once it's been passed. */
private fun remainingMs(limitMinutes: Int?, usedMs: Long): Long? =
    if (limitMinutes == null || limitMinutes == 0) null else limitMinutes * 60_000L - usedMs
