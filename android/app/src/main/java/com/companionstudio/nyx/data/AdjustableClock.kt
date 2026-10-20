package com.companionstudio.nyx.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The app's clock. It is the system clock, except while the debug tools run time faster (D-67):
 * from the moment [runFaster] is called, time moves [speed] times as fast. Release builds never change it.
 */
class AdjustableClock(private val system: Clock = Clock.systemUTC()) : Clock() {

    private class Anchor(val real: Instant, val shown: Instant, val speed: Int)

    @Volatile
    private var anchor = system.instant().let { Anchor(real = it, shown = it, speed = 1) }

    /** How many times faster than real time the clock runs. Waits in the engine are divided by it. */
    val speed: Int get() = anchor.speed

    override fun instant(): Instant {
        val current = anchor
        val realElapsed = Duration.between(current.real, system.instant())
        return current.shown.plus(realElapsed.multipliedBy(current.speed.toLong()))
    }

    /** Time carries on from where it is now, [speed] times as fast. */
    fun runFaster(speed: Int) {
        require(speed >= 1) { "speed must be at least 1, was $speed" }
        anchor = Anchor(real = system.instant(), shown = instant(), speed = speed)
    }

    /** Back to the system clock, which may jump back in time after running faster. */
    fun reset() {
        anchor = system.instant().let { Anchor(real = it, shown = it, speed = 1) }
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    // Day keys use DayClock's zone, so nothing needs this clock in another zone
    override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException("AdjustableClock is UTC only")
}
