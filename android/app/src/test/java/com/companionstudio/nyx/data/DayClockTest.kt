package com.companionstudio.nyx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DayClockTest {

    private fun at(localDateTime: String, zone: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(ZoneId.of(zone)).toInstant()

    private fun dayClockIn(zone: String) = DayClock(zone = { ZoneId.of(zone) })

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun resetAtMidnight_lastSecondBelongsToTheDay_midnightStartsTheNext() {
        val dayClock = dayClockIn("Asia/Kolkata")

        assertEquals(date("2026-10-19"), dayClock.dayKeyAt(at("2026-10-19T23:59:59", "Asia/Kolkata"), resetHour = 0))
        assertEquals(date("2026-10-20"), dayClock.dayKeyAt(at("2026-10-20T00:00:00", "Asia/Kolkata"), resetHour = 0))
    }

    @Test
    fun resetAt4_before4BelongsToPreviousDay_4StartsTheNewDay() {
        val dayClock = dayClockIn("Asia/Kolkata")

        assertEquals(date("2026-10-19"), dayClock.dayKeyAt(at("2026-10-20T03:59:59", "Asia/Kolkata"), resetHour = 4))
        assertEquals(date("2026-10-20"), dayClock.dayKeyAt(at("2026-10-20T04:00:00", "Asia/Kolkata"), resetHour = 4))
    }

    @Test
    fun timeZoneChange_sameInstantUsesTheNewZone() {
        var zone = ZoneId.of("Asia/Kolkata")
        val dayClock = DayClock(zone = { zone })
        val instant = Instant.parse("2026-10-20T01:00:00Z") // 06:30 in Kolkata, 21:00 the evening before in New York

        assertEquals(date("2026-10-20"), dayClock.dayKeyAt(instant, resetHour = 0))
        zone = ZoneId.of("America/New_York")
        assertEquals(date("2026-10-19"), dayClock.dayKeyAt(instant, resetHour = 0))
    }

    @Test
    fun dstFreeZones_resetHourBoundaryIsExact() {
        val utc = dayClockIn("UTC")
        assertEquals(date("2026-10-19"), utc.dayKeyAt(Instant.parse("2026-10-20T05:59:59Z"), resetHour = 6))
        assertEquals(date("2026-10-20"), utc.dayKeyAt(Instant.parse("2026-10-20T06:00:00Z"), resetHour = 6))

        // India has no daylight saving: the boundary is the same on any date of the year
        val kolkata = dayClockIn("Asia/Kolkata")
        assertEquals(date("2026-03-28"), kolkata.dayKeyAt(at("2026-03-29T00:59:59", "Asia/Kolkata"), resetHour = 1))
        assertEquals(date("2026-03-29"), kolkata.dayKeyAt(at("2026-03-29T01:00:00", "Asia/Kolkata"), resetHour = 1))
    }

    @Test
    fun dstZone_springForward_followsTheWallClock() {
        // London 2026-03-29: clocks jump from 01:00 GMT to 02:00 BST
        val london = dayClockIn("Europe/London")

        assertEquals(date("2026-03-28"), london.dayKeyAt(Instant.parse("2026-03-29T00:59:59Z"), resetHour = 1))
        assertEquals(date("2026-03-29"), london.dayKeyAt(Instant.parse("2026-03-29T01:00:00Z"), resetHour = 1))
    }

    @Test
    fun today_usesTheInjectedClock() {
        val clock = Clock.fixed(Instant.parse("2026-10-19T22:30:00Z"), ZoneOffset.UTC) // 04:00 on the 20th in Kolkata
        val dayClock = DayClock(clock = clock, zone = { ZoneId.of("Asia/Kolkata") })

        assertEquals(date("2026-10-20"), dayClock.today(resetHour = 0))
        assertEquals(date("2026-10-20"), dayClock.today(resetHour = 4))
        assertEquals(date("2026-10-19"), dayClock.today(resetHour = 5))
    }

    @Test
    fun resetHourOutside0To23_isRejected() {
        val dayClock = dayClockIn("UTC")
        val now = Instant.parse("2026-10-19T12:00:00Z")

        assertThrows(IllegalArgumentException::class.java) { dayClock.dayKeyAt(now, resetHour = -1) }
        assertThrows(IllegalArgumentException::class.java) { dayClock.dayKeyAt(now, resetHour = 24) }
    }
}
