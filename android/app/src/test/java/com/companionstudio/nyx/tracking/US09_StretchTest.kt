package com.companionstudio.nyx.tracking

import com.companionstudio.nyx.tracking.CreditingTestKit.CHROME
import com.companionstudio.nyx.tracking.CreditingTestKit.INSTAGRAM
import com.companionstudio.nyx.tracking.CreditingTestKit.day
import com.companionstudio.nyx.tracking.CreditingTestKit.paused
import com.companionstudio.nyx.tracking.CreditingTestKit.resumed
import com.companionstudio.nyx.tracking.CreditingTestKit.rules
import com.companionstudio.nyx.tracking.CreditingTestKit.screenOff
import com.companionstudio.nyx.tracking.CreditingTestKit.screenOn
import com.companionstudio.nyx.tracking.CreditingTestKit.t
import org.junit.Assert.assertEquals
import org.junit.Test

class US09_StretchTest {

    private fun minutes(state: TrackerState) = state.stretchMs / 60_000

    @Test
    fun onlyStretchCategories_addToIt() {
        val events = listOf(resumed(INSTAGRAM, t("10:00")), paused(INSTAGRAM, t("10:10")), resumed(CHROME, t("10:10")))

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:15"), rules())

        assertEquals(10, minutes(result.state))
    }

    @Test
    fun otherApps_dontResetIt() {
        val events = listOf(
            resumed(INSTAGRAM, t("10:00")),
            paused(INSTAGRAM, t("10:10")),
            resumed(CHROME, t("10:10")),
            paused(CHROME, t("10:15")),
            resumed(INSTAGRAM, t("10:15")),
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:20"), rules())

        assertEquals(15, minutes(result.state))
    }

    @Test
    fun D27_screenOffForTheBreakLength_resetsIt() {
        val events = listOf(
            resumed(INSTAGRAM, t("10:00")),
            paused(INSTAGRAM, t("10:10")),
            screenOff(t("10:10")),
            screenOn(t("10:15")), // exactly 5 minutes
            resumed(INSTAGRAM, t("10:15")),
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:20"), rules(breakMinutes = 5))

        assertEquals(5, minutes(result.state))
    }

    @Test
    fun D27_shorterScreenOff_doesntResetIt() {
        val events = listOf(
            resumed(INSTAGRAM, t("10:00")),
            paused(INSTAGRAM, t("10:10")),
            screenOff(t("10:10")),
            screenOn(t("10:14")),
            resumed(INSTAGRAM, t("10:14")),
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:20"), rules(breakMinutes = 5))

        assertEquals(16, minutes(result.state))
    }

    @Test
    fun dayReset_startsANewStretch() {
        val events = listOf(resumed(INSTAGRAM, t("23:50", date = "2026-10-20")))

        val result = advance(TrackerState(at = t("23:50", date = "2026-10-20")), events, until = t("00:05"), rules())

        assertEquals(5, minutes(result.state))
        assertEquals(day("2026-10-21"), result.state.stretchDay)
    }

    @Test
    fun aSecondScreenOff_keepsTheFirstOffTime() {
        val events = listOf(
            resumed(INSTAGRAM, t("10:00")),
            screenOff(t("10:10")),
            screenOff(t("10:13")),
            screenOn(t("10:15")),
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:20"), rules(breakMinutes = 5))

        assertEquals(5, minutes(result.state)) // 10:10 → 10:15 was a break; Instagram is still in front after it
    }
}
