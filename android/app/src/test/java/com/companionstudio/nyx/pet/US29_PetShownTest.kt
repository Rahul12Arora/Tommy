package com.companionstudio.nyx.pet

import com.companionstudio.nyx.data.AdjustableClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class US29_PetShownTest {

    private val start = Instant.parse("2026-10-21T10:00:00Z")

    /** The system clock follows the test's virtual time, plus [jump] for a phone that slept through a timer. */
    private class VirtualClock(private val scope: TestScope, private val start: Instant) : Clock() {
        var jump: Duration = Duration.ZERO
        override fun instant(): Instant = start.plusMillis(scope.testScheduler.currentTime).plus(jump)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    private class Fixture(val hiddenUntil: MutableStateFlow<Instant?>, val recheck: MutableStateFlow<Int>, val seen: List<Boolean>)

    private fun TestScope.observe(clock: AdjustableClock, hiddenUntil: Instant?): Fixture {
        val until = MutableStateFlow(hiddenUntil)
        val recheck = MutableStateFlow(0)
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            petShown(until, recheck, clock).collect { seen += it }
        }
        return Fixture(until, recheck, seen)
    }

    @Test
    fun US29_neverHidden_isShown() = runTest {
        val f = observe(AdjustableClock(VirtualClock(this, start)), hiddenUntil = null)

        assertEquals(listOf(true), f.seen)
    }

    @Test
    fun US29_hidden_untilExactlyTheStoredTime_thenShown() = runTest {
        val f = observe(AdjustableClock(VirtualClock(this, start)), hiddenUntil = start.plus(Duration.ofMinutes(30)))
        assertEquals(listOf(false), f.seen)

        advanceTimeBy(Duration.ofMinutes(30).toMillis() - 1)
        runCurrent()
        assertEquals(listOf(false), f.seen)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(false, true), f.seen)
    }

    @Test
    fun US29_showPetEarly_showsAtOnce() = runTest {
        val f = observe(AdjustableClock(VirtualClock(this, start)), hiddenUntil = start.plus(Duration.ofMinutes(30)))

        advanceTimeBy(Duration.ofMinutes(5).toMillis())
        f.hiddenUntil.value = null

        assertEquals(listOf(false, true), f.seen)
    }

    @Test
    fun aHideTimeInThePast_isShown() = runTest {
        val f = observe(AdjustableClock(VirtualClock(this, start)), hiddenUntil = start.minusSeconds(1))

        assertEquals(listOf(true), f.seen)
    }

    @Test
    fun D74_aTimerThatSleptThroughTheTime_isCaughtUpAtTheNextRecheck() = runTest {
        val system = VirtualClock(this, start)
        val f = observe(AdjustableClock(system), hiddenUntil = start.plus(Duration.ofMinutes(30)))

        // Deep sleep: the wall clock moved on an hour while the coroutine timer didn't
        system.jump = Duration.ofHours(1)
        assertEquals(listOf(false), f.seen)

        f.recheck.value = 1 // screen on

        assertEquals(listOf(false, true), f.seen)
    }

    @Test
    fun D67_theDebugSpeedDividesTheWait() = runTest {
        val clock = AdjustableClock(VirtualClock(this, start))
        clock.runFaster(60)
        val f = observe(clock, hiddenUntil = clock.instant().plus(Duration.ofMinutes(30)))

        advanceTimeBy(Duration.ofSeconds(30).toMillis())
        runCurrent()

        assertEquals(listOf(false, true), f.seen)
    }
}
