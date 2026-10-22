package com.companionstudio.nyx.rules

/** [baselineMs]: today's counted screen time when the pet last spoke (or an idle line was offered). */
data class IdleStep(val baselineMs: Long, val offer: Boolean)

/**
 * An idle line is due after [everyMs] of counted screen time since the pet last spoke (US-27, D-100). The first tick of
 * an engine run ([baselineMs] `null`) and a new day (the total drops) start counting from now.
 */
fun idleStep(baselineMs: Long?, totalMs: Long, everyMs: Long, enabled: Boolean): IdleStep {
    val baseline = if (baselineMs == null || totalMs < baselineMs) totalMs else baselineMs
    if (!enabled || totalMs - baseline < everyMs) return IdleStep(baseline, offer = false)
    return IdleStep(totalMs, offer = true)
}
