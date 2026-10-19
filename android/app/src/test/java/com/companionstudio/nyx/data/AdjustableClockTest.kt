package com.companionstudio.nyx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AdjustableClockTest {

    /** A system clock the test moves by hand. */
    private class HandClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    private val start = Instant.parse("2026-10-21T10:00:00Z")
    private val system = HandClock(start)
    private val clock = AdjustableClock(system)

    @Test
    fun byDefault_itIsTheSystemClock() {
        system.now = start.plusSeconds(90)

        assertEquals(start.plusSeconds(90), clock.instant())
        assertEquals(1, clock.speed)
    }

    @Test
    fun D67_runFaster_movesTimeOnFromNow_speedTimesAsFast() {
        system.now = start.plusSeconds(10)
        clock.runFaster(60)
        system.now = start.plusSeconds(20) // 10 real seconds later

        assertEquals(start.plusSeconds(10).plus(Duration.ofMinutes(10)), clock.instant())
        assertEquals(60, clock.speed)
    }

    @Test
    fun reset_goesBackToTheSystemClock() {
        clock.runFaster(60)
        system.now = start.plusSeconds(10)
        clock.reset()

        assertEquals(start.plusSeconds(10), clock.instant())
        assertEquals(1, clock.speed)
    }

    @Test
    fun speedBelowOne_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { clock.runFaster(0) }
    }
}
