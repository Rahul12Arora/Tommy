package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.BubbleTrigger
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LinePickerTest {

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config

    @Test
    fun US03_theBandsPool_comesFirst() {
        val pool = linePool(defaults, "cheerful", BubbleTrigger.IDLE, "great")

        assertEquals(defaults.lines.getValue("cheerful").getValue("idle").getValue("great"), pool)
    }

    @Test
    fun noPoolForTheBand_orAnEmptyOne_fallsBackToAny() {
        val any = defaults.lines.getValue("calm").getValue("idle").getValue("any")
        assertEquals(any, linePool(defaults, "calm", BubbleTrigger.IDLE, "meh"))

        val emptyBandPool = defaults.copy(
            lines = defaults.lines + ("calm" to defaults.lines.getValue("calm") + ("idle" to mapOf("any" to any, "meh" to emptyList()))),
        )
        assertEquals(any, linePool(emptyBandPool, "calm", BubbleTrigger.IDLE, "meh"))
    }

    @Test
    fun US03_neverRepeatsOneOfTheLast3_overManyPicks() {
        val pool = listOf("a", "b", "c", "d")
        val picker = LinePicker(Random(42))

        val picks = List(500) { picker.pick(pool, BubbleRequest(BubbleTrigger.IDLE)) }

        picks.windowed(4).forEach { window -> assertEquals("repeat in $window", 4, window.toSet().size) }
        assertEquals(pool.toSet(), picks.toSet()) // still random over the whole pool
    }

    @Test
    fun historyIsPerTrigger() {
        val pool = listOf("a", "b", "c", "d")
        // With one shared history, the goal line would always be the one line idle hasn't used
        val goalLineWasAnIdleLine = (0..19).any { seed ->
            val picker = LinePicker(Random(seed))
            val idle = List(3) { picker.pick(pool, BubbleRequest(BubbleTrigger.IDLE)) }
            picker.pick(pool, BubbleRequest(BubbleTrigger.GOAL_MET)) in idle
        }

        assertTrue(goalLineWasAnIdleLine)
    }

    @Test
    fun smallPools_stillWork() {
        val random = Random(1)
        assertEquals("only", pickLine(listOf("only"), recent = listOf("only"), random))

        // With two lines, only the newest is left out, so they alternate
        val picker = LinePicker(random)
        val picks = List(10) { picker.pick(listOf("a", "b"), BubbleRequest(BubbleTrigger.GOAL_MET)) }
        picks.zipWithNext().forEach { (one, next) -> assertTrue(one != next) }
    }

    @Test
    fun placeholdersAreFilled_aMissingValueIsLeftAsWritten() {
        assertEquals(
            "That's 1 h of Social & scrolling today.",
            fillLine("That's {limit} of {category} today.", mapOf("limit" to "1 h", "category" to "Social & scrolling")),
        )
        assertEquals("Time to close {app}?", fillLine("Time to close {app}?", emptyMap()))
    }

    @Test
    fun thePickedLine_hasItsRequestsValuesFilledIn() {
        val picker = LinePicker(Random(3))
        val request = BubbleRequest(BubbleTrigger.FOCUS_DRIFT, values = mapOf("app" to "YouTube"))

        val line = picker.pick(listOf("{app} can wait."), request)

        assertEquals("YouTube can wait.", line)
        assertFalse(line.contains('{'))
    }
}
