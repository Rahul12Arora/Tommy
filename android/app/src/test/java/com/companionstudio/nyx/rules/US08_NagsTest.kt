package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.BubbleTrigger
import com.companionstudio.nyx.config.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** SM-02 per limit (§14.7): one Social limit of 60 min, lines repeating every 10 min. */
class US08_NagsTest {

    private val start = Instant.parse("2026-10-21T10:00:00Z")
    private val repeat = Duration.ofMinutes(10)

    private fun minutes(m: Long) = start.plus(Duration.ofMinutes(m))

    private fun social(usedMinutes: Long, covered: Boolean = true) =
        LimitCheck("limit:social", Category.SOCIAL, usedMinutes * 60_000, limitMinutes = 60, covered = covered)

    private fun stretch(usedMinutes: Long, covered: Boolean = true) =
        LimitCheck(LimitKeys.STRETCH, null, usedMinutes * 60_000, limitMinutes = 30, covered = covered)

    private fun step(states: Map<String, NagState>, check: LimitCheck, at: Instant, exceeded: Set<String> = emptySet()) =
        advanceNags(states, listOf(check), exceeded, at, repeat)

    private fun NagStep.phase(key: String = "limit:social") = states.getValue(key).phase

    private fun NagStep.triggers() = lines.map { it.trigger }

    @Test
    fun underTheLimit_nothingHappens() {
        val step = step(emptyMap(), social(59), start)

        assertEquals(NagPhase.UNDER, step.phase())
        assertTrue(step.lines.isEmpty())
        assertFalse(step.glowing)
    }

    @Test
    fun US08_reachingItInACoveredApp_glows_saysTheFirstLine_andLogsItOnce() {
        val reached = step(emptyMap(), social(60), start)

        assertEquals(NagPhase.NAGGING, reached.phase())
        assertTrue(reached.glowing)
        assertEquals(listOf(BubbleTrigger.LIMIT_REACHED), reached.triggers())
        assertEquals(listOf("limit:social"), reached.exceeded)

        val next = step(reached.states, social(61), minutes(1), exceeded = setOf("limit:social"))
        assertTrue(next.lines.isEmpty())
        assertTrue(next.exceeded.isEmpty())
    }

    @Test
    fun overButNotInACoveredApp_waitsUntilOneIsInFront() {
        val outside = step(emptyMap(), social(60, covered = false), start)

        assertEquals(NagPhase.UNDER, outside.phase())
        assertTrue(outside.exceeded.isEmpty())
        assertEquals(listOf(BubbleTrigger.LIMIT_REACHED), step(outside.states, social(60), minutes(1)).triggers())
    }

    @Test
    fun US08_whileStillCovered_itRepeatsEveryRepeatMinutes() {
        val reached = step(emptyMap(), social(60), start)
        val nineLater = step(reached.states, social(69), minutes(9), setOf("limit:social"))
        val tenLater = step(nineLater.states, social(70), minutes(10), setOf("limit:social"))

        assertTrue(nineLater.lines.isEmpty())
        assertTrue(nineLater.glowing)
        assertEquals(listOf(BubbleTrigger.LIMIT_REPEAT), tenLater.triggers())
        assertEquals(minutes(10), tenLater.states.getValue("limit:social").lastLineAt)
    }

    @Test
    fun US08_leavingPauses_withoutGlow_andComingBackGlowsAtOnce_butLinesWaitForTheInterval() {
        val reached = step(emptyMap(), social(60), start)
        val left = step(reached.states, social(62, covered = false), minutes(2), setOf("limit:social"))
        val back = step(left.states, social(62), minutes(4), setOf("limit:social"))
        val later = step(back.states, social(68), minutes(10), setOf("limit:social"))

        assertEquals(NagPhase.PAUSED, left.phase())
        assertFalse(left.glowing)
        assertTrue(left.lines.isEmpty())
        assertEquals(NagPhase.NAGGING, back.phase())
        assertTrue(back.glowing)
        assertTrue("D-94: no line 4 min after the last one", back.lines.isEmpty())
        assertEquals(listOf(BubbleTrigger.LIMIT_REPEAT), later.triggers())
    }

    @Test
    fun US09_aBreakPutsTheStretchBackUnder_andTheNextCrossingNagsWithoutASecondEvent() {
        val reached = step(emptyMap(), stretch(30), start)
        val afterBreak = step(reached.states, stretch(0), minutes(40), setOf(LimitKeys.STRETCH))
        val again = step(afterBreak.states, stretch(30), minutes(70), setOf(LimitKeys.STRETCH))

        assertEquals(listOf(LimitKeys.STRETCH), reached.exceeded)
        assertEquals(NagPhase.UNDER, afterBreak.phase(LimitKeys.STRETCH))
        assertEquals(listOf(BubbleTrigger.STRETCH_REACHED), again.triggers())
        assertTrue("once per stretch limit per day", again.exceeded.isEmpty())
    }

    @Test
    fun aLimitTurnedOff_isDropped() {
        val reached = step(emptyMap(), social(60), start)

        val off = advanceNags(reached.states, emptyList(), setOf("limit:social"), minutes(1), repeat)

        assertTrue(off.states.isEmpty())
        assertFalse(off.glowing)
    }

    @Test
    fun D93_rebuild_overAndLogged_startsPaused_soARestartNeitherNagsNorLogsAgain() {
        val rebuilt = rebuildNags(listOf(social(80)), exceededToday = setOf("limit:social"), now = start)
        val firstTick = step(rebuilt, social(80), start, setOf("limit:social"))
        val tenLater = step(firstTick.states, social(90), minutes(10), setOf("limit:social"))

        assertEquals(NagPhase.PAUSED, rebuilt.getValue("limit:social").phase)
        assertTrue(firstTick.glowing)
        assertTrue(firstTick.lines.isEmpty())
        assertTrue(firstTick.exceeded.isEmpty())
        assertEquals(listOf(BubbleTrigger.LIMIT_REPEAT), tenLater.triggers())
    }

    @Test
    fun D93_rebuild_overButNotLogged_isTreatedAsJustCrossed() {
        val rebuilt = rebuildNags(listOf(social(80)), exceededToday = emptySet(), now = start)

        val firstTick = step(rebuilt, social(80), start)

        assertEquals(NagPhase.UNDER, rebuilt.getValue("limit:social").phase)
        assertEquals(listOf("limit:social"), firstTick.exceeded)
        assertEquals(listOf(BubbleTrigger.LIMIT_REACHED), firstTick.triggers())
    }

    @Test
    fun US10_snooze_silencesOnlyThatLimit_untilItEnds() {
        val both = advanceNags(emptyMap(), listOf(social(60), stretch(30)), emptySet(), start, repeat)
        val snoozed = checkNotNull(snoozeNag(both.states, "limit:social", minutes(1), Duration.ofMinutes(10)))
        val during = advanceNags(snoozed, listOf(social(65), stretch(35)), setOf("limit:social", LimitKeys.STRETCH), minutes(5), repeat)
        val other = advanceNags(during.states, listOf(social(70), stretch(40)), setOf("limit:social", LimitKeys.STRETCH), minutes(10), repeat)
        val ended = advanceNags(other.states, listOf(social(71), stretch(41)), setOf("limit:social", LimitKeys.STRETCH), minutes(11), repeat)

        assertEquals(NagPhase.SNOOZED, during.phase())
        assertEquals(NagPhase.NAGGING, during.phase(LimitKeys.STRETCH))
        assertEquals("the stretch still nags", listOf(LimitKeys.STRETCH), other.lines.map { it.check.key })
        assertEquals(listOf(BubbleTrigger.LIMIT_REPEAT), ended.lines.filter { it.check.key == "limit:social" }.map { it.trigger })
        assertEquals(NagPhase.NAGGING, ended.phase())
    }

    @Test
    fun US10_aSnoozeEndingOutsideACoveredApp_pauses() {
        val reached = step(emptyMap(), social(60), start)
        val snoozed = checkNotNull(snoozeNag(reached.states, "limit:social", start, Duration.ofMinutes(10)))

        val ended = step(snoozed, social(60, covered = false), minutes(10), setOf("limit:social"))

        assertEquals(NagPhase.PAUSED, ended.phase())
        assertNull(ended.states.getValue("limit:social").snoozedUntil)
        assertFalse(ended.glowing)
    }

    @Test
    fun US10_onlyANaggingOrPausedLimitCanBeSnoozed() {
        val under = step(emptyMap(), social(10), start)

        assertNull(snoozeNag(under.states, "limit:social", start, Duration.ofMinutes(10)))
        assertNull(snoozeNag(under.states, "limit:games", start, Duration.ofMinutes(10)))
    }
}
