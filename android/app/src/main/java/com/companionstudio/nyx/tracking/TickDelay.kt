package com.companionstudio.nyx.tracking

import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths

/**
 * Seconds until the next tick (H-01). Fast only when a limit that covers the front app is less than one slow tick
 * away from being reached, so the crossing is seen within a fast tick. M7 (active nag) and M8 (focus) add theirs here.
 */
fun tickDelaySeconds(snapshot: TrackerSnapshot, config: EffectiveConfig): Int {
    val tracking = config.document.tracking
    val category = snapshot.frontCategory ?: return tracking.slowTickSeconds
    val stretchCovers = category in config.document.limits.stretch.categories
    val remainingMs = listOfNotNull(
        remainingMs(config.valueOf(SettingPaths.categoryDailyMinutes(category)), snapshot.todayMs[category] ?: 0),
        remainingMs(config.valueOf(SettingPaths.TOTAL_DAILY_MINUTES), snapshot.todayMs.values.sum()),
        if (stretchCovers) remainingMs(config.valueOf(SettingPaths.STRETCH_MINUTES), snapshot.stretchMs) else null,
    )
    val slowMs = tracking.slowTickSeconds * 1000L
    return if (remainingMs.any { it in 1..slowMs }) tracking.fastTickSeconds else tracking.slowTickSeconds
}

/** `null` when there's no such limit or it's off (0). Negative once it's been passed. */
private fun remainingMs(limitMinutes: Int?, usedMs: Long): Long? =
    if (limitMinutes == null || limitMinutes == 0) null else limitMinutes * 60_000L - usedMs
