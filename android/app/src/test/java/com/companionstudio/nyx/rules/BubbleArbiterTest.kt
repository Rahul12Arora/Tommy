package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.BubbleTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class BubbleArbiterTest {

    private val start = Instant.parse("2026-10-21T10:00:00Z")
    private val onScreen = BubbleConditions(petOnScreen = true, focusActive = false, minGap = Duration.ofSeconds(60))
    private val hidden = onScreen.copy(petOnScreen = false)
    private val focusing = onScreen.copy(focusActive = true)

    private fun request(trigger: BubbleTrigger, key: String = trigger.id) = BubbleRequest(trigger, key = key)

    private fun secondsLater(seconds: Long) = start.plusSeconds(seconds)

    @Test
    fun A21_whenSeveralFireTogether_theHighestTierWins_andTheRestWait() {
        val offered = BubbleTrigger.entries.reversed().map { request(it) }

        val order = generateSequence(arbitrate(ArbiterState(), offered, start, onScreen)) { previous ->
            previous.bubble?.let { arbitrate(previous.state, emptyList(), previous.state.lastShownAt!!.plusSeconds(60), onScreen) }
        }.mapNotNull { it.bubble?.trigger }.toList()

        assertEquals(
            listOf(
                // focus tier: in the order offered (reversed here)
                BubbleTrigger.FOCUS_BROKEN, BubbleTrigger.FOCUS_CHECKIN, BubbleTrigger.FOCUS_DRIFT, BubbleTrigger.FOCUS_STARTED,
                BubbleTrigger.STRETCH_REACHED,
                BubbleTrigger.LIMIT_REPEAT, BubbleTrigger.LIMIT_REACHED,
                BubbleTrigger.BREAK_DUE,
                BubbleTrigger.PET_RETURNED, BubbleTrigger.FOCUS_COMPLETED, BubbleTrigger.GOAL_MET, BubbleTrigger.BREAK_TAKEN,
                BubbleTrigger.IDLE,
            ),
            order,
        )
    }

    @Test
    fun withinATier_theOneWaitingLongestComesFirst() {
        val first = arbitrate(ArbiterState(lastShownAt = start), listOf(request(BubbleTrigger.GOAL_MET)), secondsLater(10), onScreen)
        val second = arbitrate(first.state, listOf(request(BubbleTrigger.PET_RETURNED)), secondsLater(60), onScreen)

        assertEquals(BubbleTrigger.GOAL_MET, second.bubble?.trigger)
        assertEquals(listOf(request(BubbleTrigger.PET_RETURNED)), second.state.pending)
    }

    @Test
    fun A21_theGapHoldsABubbleBack_thenLetsItShow() {
        val shown = arbitrate(ArbiterState(), listOf(request(BubbleTrigger.PET_RETURNED)), start, onScreen)
        assertEquals(BubbleTrigger.PET_RETURNED, shown.bubble?.trigger)

        val tooSoon = arbitrate(shown.state, listOf(request(BubbleTrigger.GOAL_MET)), secondsLater(59), onScreen)
        assertNull(tooSoon.bubble)
        assertEquals(listOf(request(BubbleTrigger.GOAL_MET)), tooSoon.state.pending)

        val afterGap = arbitrate(tooSoon.state, emptyList(), secondsLater(60), onScreen)
        assertEquals(BubbleTrigger.GOAL_MET, afterGap.bubble?.trigger)
        assertEquals(secondsLater(60), afterGap.state.lastShownAt)
        assertEquals(emptyList<BubbleRequest>(), afterGap.state.pending)
    }

    @Test
    fun A21_focusDrift_ignoresTheGap_butOtherFocusBubblesDont() {
        val justShown = ArbiterState(lastShownAt = start)

        assertEquals(
            BubbleTrigger.FOCUS_DRIFT,
            arbitrate(justShown, listOf(request(BubbleTrigger.FOCUS_DRIFT)), secondsLater(1), onScreen).bubble?.trigger,
        )
        assertNull(arbitrate(justShown, listOf(request(BubbleTrigger.FOCUS_CHECKIN)), secondsLater(1), onScreen).bubble)
    }

    @Test
    fun aGapOfZero_letsABubbleShowEveryTick_stillOnlyOnePerTick() {
        val noGap = onScreen.copy(minGap = Duration.ZERO)
        val offered = listOf(request(BubbleTrigger.GOAL_MET), request(BubbleTrigger.BREAK_TAKEN))

        val first = arbitrate(ArbiterState(lastShownAt = start), offered, start, noGap)
        val second = arbitrate(first.state, emptyList(), start, noGap)

        assertEquals(BubbleTrigger.GOAL_MET, first.bubble?.trigger)
        assertEquals(BubbleTrigger.BREAK_TAKEN, second.bubble?.trigger)
    }

    @Test
    fun aNewerRequestWithTheSameKey_replacesTheWaitingOne() {
        val waiting = ArbiterState(
            pending = listOf(request(BubbleTrigger.LIMIT_REACHED, key = "social"), request(BubbleTrigger.LIMIT_REACHED, key = "games")),
            lastShownAt = start,
        )
        val newer = BubbleRequest(BubbleTrigger.LIMIT_REPEAT, values = mapOf("app" to "Instagram"), key = "social")

        val result = arbitrate(waiting, listOf(newer), secondsLater(5), onScreen)

        assertEquals(listOf(request(BubbleTrigger.LIMIT_REACHED, key = "games"), newer), result.state.pending)
    }

    @Test
    fun D24_petNotOnScreen_nagsAndBreaksBecomeNotifications_othersAreDropped() {
        val offered = listOf(
            request(BubbleTrigger.LIMIT_REACHED),
            request(BubbleTrigger.STRETCH_REACHED),
            request(BubbleTrigger.BREAK_DUE),
            request(BubbleTrigger.PET_RETURNED),
            request(BubbleTrigger.IDLE),
            request(BubbleTrigger.FOCUS_STARTED),
        )

        val result = arbitrate(ArbiterState(lastShownAt = start), offered, secondsLater(1), hidden)

        assertNull(result.bubble)
        assertEquals(offered.take(3), result.notifications)
        assertEquals(emptyList<BubbleRequest>(), result.state.pending)
        assertEquals(start, result.state.lastShownAt) // a notification isn't a bubble on screen
    }

    @Test
    fun D25_duringFocus_onlyFocusBubblesShow_othersWait_idleIsDropped() {
        val offered = listOf(request(BubbleTrigger.LIMIT_REACHED), request(BubbleTrigger.GOAL_MET), request(BubbleTrigger.IDLE))

        val held = arbitrate(ArbiterState(), offered, start, focusing)
        assertNull(held.bubble)
        assertEquals(offered.take(2), held.state.pending)

        val checkIn = arbitrate(held.state, listOf(request(BubbleTrigger.FOCUS_CHECKIN)), start, focusing)
        assertEquals(BubbleTrigger.FOCUS_CHECKIN, checkIn.bubble?.trigger)

        // The session ends: the held nag comes first
        val ended = arbitrate(checkIn.state, emptyList(), secondsLater(60), onScreen)
        assertEquals(BubbleTrigger.LIMIT_REACHED, ended.bubble?.trigger)
    }

    @Test
    fun D25_focusAndHidden_heldRequestsStayHeld_focusBubblesAreDropped() {
        val offered = listOf(request(BubbleTrigger.LIMIT_REACHED), request(BubbleTrigger.FOCUS_DRIFT))

        val result = arbitrate(ArbiterState(), offered, start, focusing.copy(petOnScreen = false))

        assertNull(result.bubble)
        assertEquals(emptyList<BubbleRequest>(), result.notifications)
        assertEquals(listOf(request(BubbleTrigger.LIMIT_REACHED)), result.state.pending)
    }

    @Test
    fun nothingOffered_nothingWaiting_nothingShows() {
        val result = arbitrate(ArbiterState(), emptyList(), start, onScreen)

        assertNull(result.bubble)
        assertEquals(ArbiterState(), result.state)
    }
}
