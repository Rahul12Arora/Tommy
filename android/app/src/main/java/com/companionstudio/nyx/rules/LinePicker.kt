package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.ANY_POOL
import com.companionstudio.nyx.config.BubbleTrigger
import com.companionstudio.nyx.config.NyxConfig
import kotlin.random.Random

/** A line never repeats one of the last this many lines of its trigger (§4). */
private const val NO_REPEAT = 3

private val PLACEHOLDER = Regex("\\{([a-z_]+)\\}")

/**
 * The lines [personalityId] can say for [trigger] at mood band [bandId]: that band's pool if it has lines, otherwise
 * `any`, which the validator guarantees for every personality and trigger (§14.2).
 */
fun linePool(document: NyxConfig, personalityId: String, trigger: BubbleTrigger, bandId: String): List<String> {
    val pools = document.lines[personalityId]?.get(trigger.id)
    return pools?.get(bandId)?.takeIf { it.isNotEmpty() } ?: checkNotNull(pools?.get(ANY_POOL)) {
        "No '$ANY_POOL' lines for $personalityId/${trigger.id}"
    }
}

/** A random line from [pool] that isn't among the [recent] ones; with a small pool, only the newest are left out. */
fun pickLine(pool: List<String>, recent: List<String>, random: Random): String {
    val leftOut = recent.takeLast(minOf(NO_REPEAT, pool.size - 1)).toSet()
    return pool.filterNot { it in leftOut }.random(random)
}

/** Replaces each `{name}` that has a value; one without a value is left as written. */
fun fillLine(line: String, values: Map<String, String>): String =
    PLACEHOLDER.replace(line) { match -> values[match.groupValues[1]] ?: match.value }

/** Picks lines and remembers the recent ones per trigger, in memory: a restart may repeat a line once (D-83). */
class LinePicker(private val random: Random) {
    private val recent = HashMap<BubbleTrigger, List<String>>()

    fun pick(pool: List<String>, request: BubbleRequest): String {
        val history = recent[request.trigger].orEmpty()
        val line = pickLine(pool, history, random)
        recent[request.trigger] = (history + line).takeLast(NO_REPEAT)
        return fillLine(line, request.values)
    }
}
