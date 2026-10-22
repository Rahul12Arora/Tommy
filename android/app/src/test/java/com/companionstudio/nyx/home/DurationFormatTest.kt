package com.companionstudio.nyx.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DurationFormatTest {

    @Test
    fun underAnHour_showsMinutesOnly_droppingPartialMinutes() {
        assertEquals("0 min", formatDuration(0))
        assertEquals("0 min", formatDuration(59))
        assertEquals("1 min", formatDuration(60))
        assertEquals("59 min", formatDuration(3599))
    }

    @Test
    fun wholeHours_showHoursOnly() {
        assertEquals("1 h", formatDuration(3600))
    }

    @Test
    fun hoursAndMinutes_showBoth() {
        assertEquals("3 h 12 min", formatDuration(11_520))
    }

    @Test
    fun negative_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { formatDuration(-1) }
        assertThrows(IllegalArgumentException::class.java) { formatShort(-1) }
    }

    @Test
    fun D101_short_fitsTheRingsSlots() {
        assertEquals("0m", formatShort(59))
        assertEquals("47m", formatShort(47 * 60))
        assertEquals("2h", formatShort(2 * 3600))
        assertEquals("1h05", formatShort(3600 + 5 * 60))
        assertEquals("3h12", formatShort(11_520))
    }
}
