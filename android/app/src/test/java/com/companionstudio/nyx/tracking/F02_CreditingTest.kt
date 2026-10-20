package com.companionstudio.nyx.tracking

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.tracking.CreditingTestKit.CHROME
import com.companionstudio.nyx.tracking.CreditingTestKit.DOCS
import com.companionstudio.nyx.tracking.CreditingTestKit.INSTAGRAM
import com.companionstudio.nyx.tracking.CreditingTestKit.LAUNCHER
import com.companionstudio.nyx.tracking.CreditingTestKit.categories
import com.companionstudio.nyx.tracking.CreditingTestKit.day
import com.companionstudio.nyx.tracking.CreditingTestKit.paused
import com.companionstudio.nyx.tracking.CreditingTestKit.resumed
import com.companionstudio.nyx.tracking.CreditingTestKit.rules
import com.companionstudio.nyx.tracking.CreditingTestKit.screenOff
import com.companionstudio.nyx.tracking.CreditingTestKit.screenOn
import com.companionstudio.nyx.tracking.CreditingTestKit.seconds
import com.companionstudio.nyx.tracking.CreditingTestKit.t
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class F02_CreditingTest {

    private val today = day("2026-10-21")

    @Test
    fun appSwitches_creditEachAppItsOwnTime() {
        val events = listOf(
            resumed(INSTAGRAM, t("10:00")),
            paused(INSTAGRAM, t("10:10")),
            resumed(CHROME, t("10:10")),
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:15"), rules())

        assertEquals(
            mapOf(
                Triple(today, INSTAGRAM, Category.SOCIAL) to 600L,
                Triple(today, CHROME, Category.NEUTRAL) to 300L,
            ),
            result.seconds(),
        )
        assertEquals(t("10:15"), result.state.at)
    }

    @Test
    fun timeIsCreditedUpToUntil_andTheAppStaysInFrontForTheNextTick() {
        val first = advance(TrackerState(at = t("10:00")), listOf(resumed(INSTAGRAM, t("10:00"))), t("10:01"), rules())
        val second = advance(first.state, emptyList(), until = t("10:03"), rules())

        assertEquals(mapOf(Triple(today, INSTAGRAM, Category.SOCIAL) to 60L), first.seconds())
        assertEquals(mapOf(Triple(today, INSTAGRAM, Category.SOCIAL) to 120L), second.seconds())
        assertEquals(INSTAGRAM, second.state.front?.packageName)
    }

    @Test
    fun anotherScreenOfTheSameApp_andALatePause_loseNoTime() {
        val events = listOf(
            resumed(INSTAGRAM, t("10:00"), activity = "Feed"),
            resumed(INSTAGRAM, t("10:05"), activity = "Reels"),
            paused(INSTAGRAM, t("10:05"), activity = "Feed"), // arrives after the next screen's resume
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:10"), rules())

        assertEquals(mapOf(Triple(today, INSTAGRAM, Category.SOCIAL) to 600L), result.seconds())
    }

    @Test
    fun screenOff_isNotCounted_evenIfTheAppNeverPaused() {
        val events = listOf(
            resumed(INSTAGRAM, t("10:00")),
            screenOff(t("10:05")),
            screenOn(t("10:20")),
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:25"), rules())

        assertEquals(mapOf(Triple(today, INSTAGRAM, Category.SOCIAL) to 600L), result.seconds())
    }

    @Test
    fun A20_excludedApps_areNotCounted() {
        val events = listOf(
            resumed(LAUNCHER, t("10:00")),
            paused(LAUNCHER, t("10:05")),
            resumed(INSTAGRAM, t("10:05")),
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:06"), rules())

        assertEquals(mapOf(Triple(today, INSTAGRAM, Category.SOCIAL) to 60L), result.seconds())
    }

    @Test
    fun nothingInFront_isNotCounted() {
        val events = listOf(resumed(INSTAGRAM, t("10:00")), paused(INSTAGRAM, t("10:01")))

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:30"), rules())

        assertEquals(mapOf(Triple(today, INSTAGRAM, Category.SOCIAL) to 60L), result.seconds())
        assertNull(result.state.front)
    }

    @Test
    fun segmentOverTheResetHour_isSplitOntoTwoDays() {
        val events = listOf(resumed(INSTAGRAM, t("03:50")))

        val result = advance(TrackerState(at = t("03:50")), events, until = t("04:10"), rules(resetHour = 4))

        assertEquals(
            mapOf(
                Triple(day("2026-10-20"), INSTAGRAM, Category.SOCIAL) to 600L,
                Triple(day("2026-10-21"), INSTAGRAM, Category.SOCIAL) to 600L,
            ),
            result.seconds(),
        )
    }

    @Test
    fun US04_categoryChangedBetweenTicks_appliesFromThenOn() {
        val first = advance(TrackerState(at = t("10:00")), listOf(resumed(INSTAGRAM, t("10:00"))), t("10:10"), rules())
        val changed = categories + (INSTAGRAM to Category.PRODUCTIVE)
        val second = advance(first.state, emptyList(), t("10:20"), rules(categoryOf = changed))

        assertEquals(mapOf(Triple(today, INSTAGRAM, Category.SOCIAL) to 600L), first.seconds())
        assertEquals(mapOf(Triple(today, INSTAGRAM, Category.PRODUCTIVE) to 600L), second.seconds())
    }

    @Test
    fun D62_timeBeforeTheCursor_isReplayedButNotCreditedAgain() {
        // After a restart: replay from 09:00, credited up to 10:00 already
        val events = listOf(resumed(INSTAGRAM, t("09:30")))

        val result = advance(TrackerState(at = t("09:00")), events, until = t("10:10"), rules(creditFrom = t("10:00")))

        assertEquals(mapOf(Triple(today, INSTAGRAM, Category.SOCIAL) to 600L), result.seconds())
        assertEquals(INSTAGRAM, result.state.front?.packageName)
        assertEquals(40 * 60_000L, result.state.stretchMs) // the replayed half hour still counts towards the stretch
    }

    @Test
    fun shutdown_clearsTheFrontApp_andStartupTurnsTheScreenOn() {
        val events = listOf(
            resumed(INSTAGRAM, t("10:00")),
            UsageEvent(UsageEventType.DEVICE_SHUTDOWN, t("10:05")),
            UsageEvent(UsageEventType.DEVICE_STARTUP, t("10:07")),
            resumed(DOCS, t("10:08")),
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:10"), rules())

        assertEquals(
            mapOf(
                Triple(today, INSTAGRAM, Category.SOCIAL) to 300L,
                Triple(today, DOCS, Category.PRODUCTIVE) to 120L,
            ),
            result.seconds(),
        )
        assertEquals(true, result.state.screenOn)
    }

    @Test
    fun creditsForTheSameAppDayAndCategory_areAddedTogether() {
        val events = listOf(
            resumed(INSTAGRAM, t("10:00")),
            paused(INSTAGRAM, t("10:05")),
            resumed(CHROME, t("10:05")),
            paused(CHROME, t("10:06")),
            resumed(INSTAGRAM, t("10:06")),
        )

        val result = advance(TrackerState(at = t("10:00")), events, until = t("10:10"), rules())

        assertEquals(2, result.credits.size)
        assertEquals(540L, result.seconds().getValue(Triple(today, INSTAGRAM, Category.SOCIAL)))
    }

    @Test
    fun untilBeforeTheState_creditsNothingAndKeepsTheState() {
        val state = TrackerState(at = t("10:00"), front = ActivityKey(INSTAGRAM, "Main"))

        val result = advance(state, emptyList(), until = t("09:00"), rules())

        assertEquals(emptyList<Credit>(), result.credits)
        assertEquals(t("10:00"), result.state.at)
    }
}
