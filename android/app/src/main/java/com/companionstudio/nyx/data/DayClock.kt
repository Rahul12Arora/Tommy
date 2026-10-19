package com.companionstudio.nyx.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Turns moments into day keys. All day logic goes through here (ARCHITECTURE §9). */
class DayClock(
    private val clock: Clock = Clock.systemUTC(),
    // A function rather than the Clock's zone: Clock.systemDefaultZone() fixes the zone when it's
    // created, while Android updates the default zone when the user changes time zones
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** The local date [instant] belongs to, when each day starts at [resetHour]:00 instead of midnight. */
    fun dayKeyAt(instant: Instant, resetHour: Int): LocalDate {
        require(resetHour in 0..23) { "resetHour must be 0–23, was $resetHour" }
        return LocalDateTime.ofInstant(instant, zone()).minusHours(resetHour.toLong()).toLocalDate()
    }

    fun today(resetHour: Int): LocalDate = dayKeyAt(clock.instant(), resetHour)

    /** The moment [day] starts: [resetHour]:00 local time on that date (later if a DST gap swallows that hour). */
    fun startOfDay(day: LocalDate, resetHour: Int): Instant {
        require(resetHour in 0..23) { "resetHour must be 0–23, was $resetHour" }
        return day.atTime(resetHour, 0).atZone(zone()).toInstant()
    }

    /** The first moment after [instant] that belongs to the next day key. Time is split here when credited (§14.4). */
    fun nextDayStart(instant: Instant, resetHour: Int): Instant =
        startOfDay(dayKeyAt(instant, resetHour).plusDays(1), resetHour)
}
