package com.companionstudio.nyx.debug

import com.companionstudio.nyx.tracking.UsageEvent
import com.companionstudio.nyx.tracking.UsageEventType
import com.companionstudio.nyx.tracking.UsageSource
import java.time.Duration
import java.time.Instant

/** One line of a scenario file (§14.4). */
sealed interface ScenarioStep {
    val duration: Duration

    /** [packageName] is in front for [duration]. */
    data class App(val packageName: String, override val duration: Duration) : ScenarioStep

    /** Screen on, nothing counted in front. */
    data class Idle(override val duration: Duration) : ScenarioStep

    data class ScreenOff(override val duration: Duration) : ScenarioStep
}

/**
 * Reads a scenario: one step per line, `#` starts a comment. Any line it doesn't understand rejects the whole file,
 * so a typo never plays half a scenario.
 */
fun parseScenario(text: String): List<ScenarioStep> =
    text.lines().mapIndexedNotNull { index, rawLine ->
        val line = rawLine.substringBefore('#').trim()
        if (line.isEmpty()) return@mapIndexedNotNull null
        val parts = line.split(Regex("\\s+"))
        val step = when {
            parts[0] == "app" && parts.size == 3 -> parseDuration(parts[2])?.let { ScenarioStep.App(parts[1], it) }
            parts[0] == "idle" && parts.size == 2 -> parseDuration(parts[1])?.let { ScenarioStep.Idle(it) }
            parts[0] == "screen_off" && parts.size == 2 -> parseDuration(parts[1])?.let { ScenarioStep.ScreenOff(it) }
            else -> null
        }
        step ?: throw IllegalArgumentException("Scenario line ${index + 1} not understood: $rawLine")
    }

/** `90s`, `65m`, `2h`; `null` for anything else. */
private fun parseDuration(text: String): Duration? {
    val amount = text.dropLast(1).toLongOrNull()?.takeIf { it > 0 } ?: return null
    return when (text.last()) {
        's' -> Duration.ofSeconds(amount)
        'm' -> Duration.ofMinutes(amount)
        'h' -> Duration.ofHours(amount)
        else -> null
    }
}

/**
 * The events the steps produce, played from [start] with the screen on. It starts with a screen-on, so continuous use
 * counts from the scenario's start, not from the tracker's look-back (BUG-02).
 */
fun scenarioEvents(steps: List<ScenarioStep>, start: Instant): List<UsageEvent> {
    val events = mutableListOf(UsageEvent(UsageEventType.SCREEN_ON, start))
    var at = start
    for (step in steps) {
        val end = at.plus(step.duration)
        when (step) {
            is ScenarioStep.App -> {
                events += UsageEvent(UsageEventType.APP_RESUMED, at, step.packageName, SCENARIO_ACTIVITY)
                events += UsageEvent(UsageEventType.APP_PAUSED, end, step.packageName, SCENARIO_ACTIVITY)
            }
            is ScenarioStep.Idle -> Unit
            is ScenarioStep.ScreenOff -> {
                events += UsageEvent(UsageEventType.SCREEN_OFF, at)
                events += UsageEvent(UsageEventType.SCREEN_ON, end)
            }
        }
        at = end
    }
    return events
}

/** Plays a scenario instead of Android's event log. */
class ScenarioUsageSource(private val events: List<UsageEvent>) : UsageSource {
    override fun events(from: Instant, to: Instant): List<UsageEvent> = events.filter { it.at >= from && it.at < to }
}

private const val SCENARIO_ACTIVITY = "scenario"
