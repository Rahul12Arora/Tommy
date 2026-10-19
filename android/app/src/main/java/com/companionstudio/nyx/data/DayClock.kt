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
}
