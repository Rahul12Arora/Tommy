package com.companionstudio.nyx.tracking

import com.companionstudio.nyx.tracking.CreditingTestKit.CHROME
import com.companionstudio.nyx.tracking.CreditingTestKit.LAUNCHER
import com.companionstudio.nyx.tracking.CreditingTestKit.paused
import com.companionstudio.nyx.tracking.CreditingTestKit.resumed
import com.companionstudio.nyx.tracking.CreditingTestKit.rules
import com.companionstudio.nyx.tracking.CreditingTestKit.screenOff
import com.companionstudio.nyx.tracking.CreditingTestKit.screenOn
import com.companionstudio.nyx.tracking.CreditingTestKit.t
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Continuous screen use: every screen-on moment since the last break, whatever is in front (A-31, D-96). */
class US30_ContinuousUseTest {

    private fun minutes(state: TrackerState) = state.continuousMs / 60_000

    /** A tracker that has already seen a screen event, so it knows the screen is on. */
    private fun known(at: Instant) = TrackerState(at = at, screenKnown = true)

    @Test
    fun BUG02_beforeTheFirstScreenEvent_nothingCounts_thenItDoes() {
        val unknown = advance(TrackerState(at = t("10:00")), listOf(resumed(CHROME, t("10:00"))), until = t("11:00"), rules())
        val seen = advance(TrackerState(at = t("10:00")), listOf(resumed(CHROME, t("10:00")), screenOn(t("10:30"))), until = t("11:00"), rules())

        assertEquals(0, minutes(unknown.state))
        assertEquals(60, unknown.credits.sumOf { it.durationMs } / 60_000) // apps are still credited
        assertEquals(30, minutes(seen.state))
    }

    @Test
    fun countsEveryScreenOnMoment_launcherAndNothingInFrontIncluded() {
        val events = listOf(resumed(LAUNCHER, t("10:00")), paused(LAUNCHER, t("10:05")), resumed(CHROME, t("10:05")), paused(CHROME, t("10:10")))

        val result = advance(known(t("10:00")), events, until = t("10:15"), rules())

        assertEquals(15, minutes(result.state))
    }

    @Test
    fun aShortScreenOff_doesntCount_andDoesntReset() {
        val events = listOf(resumed(CHROME, t("10:00")), screenOff(t("10:10")), screenOn(t("10:14")))

        val result = advance(known(t("10:00")), events, until = t("10:20"), rules(breakMinutes = 5))

        assertEquals(16, minutes(result.state))
        assertEquals(emptyList<EndedBreak>(), result.breaks)
    }

    @Test
    fun D97_aBreak_resetsIt_andIsReported_withTheUseBeforeIt() {
        val events = listOf(resumed(CHROME, t("10:00")), screenOff(t("10:50")), screenOn(t("10:56")))

        val result = advance(known(t("10:00")), events, until = t("11:00"), rules(breakMinutes = 5))

        assertEquals(4, minutes(result.state))
        assertEquals(listOf(EndedBreak(t("10:56"), continuousBeforeMs = 50 * 60_000)), result.breaks)
    }

    @Test
    fun D96_theDayResetDoesntResetIt() {
        val events = listOf(resumed(CHROME, t("23:50", date = "2026-10-21")))

        val result = advance(known(t("23:50", date = "2026-10-21")), events, until = t("00:10", date = "2026-10-22"), rules())

        assertEquals(20, minutes(result.state))
        assertEquals(0, result.state.stretchMs) // Chrome isn't a stretch app; the stretch day moved on
    }

    @Test
    fun D62_aBreakBeforeTheCursor_isOnlyReplayed_notReportedAgain() {
        val events = listOf(resumed(CHROME, t("10:00")), screenOff(t("10:50")), screenOn(t("10:56")))

        val result = advance(known(t("10:00")), events, until = t("11:00"), rules(creditFrom = t("10:58")))

        assertEquals(emptyList<EndedBreak>(), result.breaks)
        assertEquals("the replay still rebuilds it", 4, minutes(result.state))
    }
}
