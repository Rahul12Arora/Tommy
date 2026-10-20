package com.companionstudio.nyx.pet

import com.companionstudio.nyx.data.AdjustableClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import java.time.Duration
import java.time.Instant

/** Hiding the pet for a while and bringing it back (US-29, D-74). Screens and the overlay both use it. */
class PetVisibility(private val settings: PetSettings, private val clock: AdjustableClock) {

    /** [recheck]: the service passes its screen-on flow, see [petShown]. */
    fun shown(recheck: Flow<Any> = flowOf(Unit)): Flow<Boolean> = petShown(settings.hiddenUntil, recheck, clock)

    suspend fun hide(minutes: Int) {
        settings.setHiddenUntil(clock.instant().plus(Duration.ofMinutes(minutes.toLong())))
    }

    suspend fun show() {
        settings.setHiddenUntil(null)
    }
}

/**
 * `false` until [hiddenUntil] has passed, then `true`. The wait is divided by the debug clock's speed (D-67).
 * Every [recheck] emission reads the clock again: a coroutine timer can stall while the phone is in deep sleep,
 * so the pet is back at the next screen-on at the latest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun petShown(hiddenUntil: Flow<Instant?>, recheck: Flow<Any>, clock: AdjustableClock): Flow<Boolean> =
    combine(hiddenUntil, recheck) { until, _ -> until }
        .transformLatest { until ->
            while (true) {
                val left = until?.let { Duration.between(clock.instant(), it) }
                if (left == null || left <= Duration.ZERO) {
                    emit(true)
                    return@transformLatest
                }
                emit(false)
                // Rounded up, so the check after the wait never lands a moment early
                val realMillis = (left.toNanos() + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI
                delay((realMillis + clock.speed - 1) / clock.speed)
            }
        }
        .distinctUntilChanged()

private const val NANOS_PER_MILLI = 1_000_000L
