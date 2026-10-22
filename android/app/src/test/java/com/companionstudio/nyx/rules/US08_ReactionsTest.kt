package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.focus.FakeFocusSessionDao
import com.companionstudio.nyx.focus.FocusSessions
import com.companionstudio.nyx.tracking.EndedBreak
import com.companionstudio.nyx.tracking.TrackerSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/** M7 end to end through [Reactions] with the default config and a fake log (§14.7). */
class US08_ReactionsTest {

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
    private val config = EffectiveConfig(defaults, emptyMap())
    private val clock = TestClock(Instant.parse("2026-10-21T10:00:00Z"))
    private val dao = FakePetEventDao()
    private val reactions = Reactions(dao, clock, TestLabels, FocusSessions(FakeFocusSessionDao(dao), clock), Random(7))

    private fun pool(trigger: String) = defaults.lines.getValue("cheerful").getValue(trigger).getValue("any")

    private fun inInstagram(socialMinutes: Long, continuousMinutes: Long = 0, breaks: List<EndedBreak> = emptyList()) = TrackerSnapshot(
        day = LocalDate.parse("2026-10-21"),
        frontPackage = "com.instagram.android",
        frontCategory = Category.SOCIAL,
        stretchMs = 0,
        todayMs = mapOf(Category.SOCIAL to socialMinutes * 60_000),
        continuousMs = continuousMinutes * 60_000,
        endedBreaks = breaks,
    )

    private fun inChrome(continuousMinutes: Long, breaks: List<EndedBreak> = emptyList(), neutralMinutes: Long = continuousMinutes) =
        TrackerSnapshot(
            day = LocalDate.parse("2026-10-21"),
            frontPackage = "com.android.chrome",
            frontCategory = Category.NEUTRAL,
            stretchMs = 0,
            todayMs = mapOf(Category.NEUTRAL to neutralMinutes * 60_000),
            continuousMs = continuousMinutes * 60_000,
            endedBreaks = breaks,
        )

    private fun later(minutes: Long) {
        clock.now = clock.now.plus(Duration.ofMinutes(minutes))
    }

    @Test
    fun US08_reachingTheSocialLimit_glows_nags_logsTheLimitOnce_andOffersSnooze() = runTest {
        val reached = reactions.afterTick(inInstagram(socialMinutes = 60), config, personalityId = null, petOnScreen = true)
        later(1)
        val next = reactions.afterTick(inInstagram(socialMinutes = 61), config, personalityId = null, petOnScreen = true)

        assertTrue(reached.glowing)
        assertEquals("limit:social", reached.bubble?.snoozeKey)
        assertTrue(reached.bubble?.line.orEmpty().isNotBlank())
        assertFalse("placeholders are filled", reached.bubble?.line.orEmpty().contains("{"))
        assertEquals(listOf("limit:social"), dao.events.filter { it.type == PetEventType.LIMIT_EXCEEDED }.map { it.subject })
        assertNull(next.bubble)
        assertTrue(next.glowing)
        // 70 − 6 doomscroll events × 3 − 10 for the limit
        assertEquals(42, reached.mood)
    }

    @Test
    fun US10_snooze_logsItsSubject_costsMood_andStopsTheGlow() = runTest {
        reactions.afterTick(inInstagram(socialMinutes = 60), config, personalityId = null, petOnScreen = true)

        assertTrue(reactions.snooze("limit:social", config))
        val after = reactions.afterTick(inInstagram(socialMinutes = 61), config, personalityId = null, petOnScreen = true)

        assertEquals("limit:social", dao.events.last { it.type == PetEventType.SNOOZED }.subject)
        assertFalse(after.glowing)
        assertEquals(37, after.mood)
        assertEquals(NagPhase.SNOOZED, after.status.nags.getValue("limit:social").phase)
    }

    @Test
    fun US10_onceTodaysSnoozesAreUsedUp_theBubbleHasNoSnooze_andSnoozingFails() = runTest {
        repeat(3) { dao.log(PetEventType.SNOOZED, subject = "stretch") }

        val reached = reactions.afterTick(inInstagram(socialMinutes = 60), config, personalityId = null, petOnScreen = true)

        assertNotNull(reached.bubble)
        assertNull(reached.bubble?.snoozeKey)
        assertFalse(reactions.snooze("limit:social", config))
    }

    @Test
    fun D24_whileHidden_aNagBecomesN04_andThereIsNoBubble() = runTest {
        val hidden = reactions.afterTick(inInstagram(socialMinutes = 60), config, personalityId = null, petOnScreen = false)

        assertNull(hidden.bubble)
        assertEquals(1, hidden.nagNotifications.size)
        assertNull(hidden.breakNotification)
    }

    @Test
    fun aNagWaitingBehindTheGap_isWithdrawn_whenTheUserLeavesTheApp() = runTest {
        val social60 = mapOf(Category.SOCIAL to 60 * 60_000L)
        // The break reminder takes the bubble; then the nag has to wait behind the 60 s gap
        val reminder = reactions.afterTick(inChrome(continuousMinutes = 45).copy(todayMs = social60), config, null, petOnScreen = true)
        val nag = reactions.afterTick(inInstagram(socialMinutes = 60, continuousMinutes = 45), config, null, petOnScreen = true)
        later(2)
        val left = reactions.afterTick(inChrome(continuousMinutes = 47).copy(todayMs = social60), config, null, petOnScreen = true)

        assertTrue(reminder.bubble?.line in pool("break_due").map { it.replace("{minutes}", "5") })
        assertNull("held back by the gap", nag.bubble)
        assertTrue(nag.glowing)
        assertNull("withdrawn: the nag is paused", left.bubble)
    }

    @Test
    fun US11_aGentleReminder_isABubbleAndN02() = runTest {
        val due = reactions.afterTick(inChrome(continuousMinutes = 45), config, personalityId = null, petOnScreen = true)

        assertNotNull(due.breakNotification)
        assertTrue(due.bubble?.line in pool("break_due").map { it.replace("{minutes}", "5") })
        assertNull(due.bubble?.snoozeKey)
        assertNull(due.breakScreen)
    }

    @Test
    fun US11_aBreakAfterTheReminder_isLogged_greetsTheUser_andCancelsN02() = runTest {
        reactions.afterTick(inChrome(continuousMinutes = 50), config, personalityId = null, petOnScreen = true)
        later(10)

        val back = reactions.afterTick(
            inChrome(continuousMinutes = 0, breaks = listOf(EndedBreak(clock.now, continuousBeforeMs = 50 * 60_000))),
            config,
            personalityId = null,
            petOnScreen = true,
        )

        assertTrue(back.breakTaken)
        assertEquals(PetEventType.BREAK_TAKEN, dao.events.last().type)
        assertTrue(back.bubble?.line in pool("break_taken"))
    }

    @Test
    fun D86_aPetThatRanAway_comesBack_afterARealBreak() = runTest {
        val away = reactions.afterTick(inInstagram(socialMinutes = 240, continuousMinutes = 240), config, null, petOnScreen = true)
        later(10)

        val back = reactions.afterTick(
            inInstagram(socialMinutes = 240, breaks = listOf(EndedBreak(clock.now, continuousBeforeMs = 240 * 60_000))).copy(frontPackage = null, frontCategory = null),
            config,
            personalityId = null,
            petOnScreen = true,
        )

        assertEquals(0, away.mood)
        assertEquals(5, back.mood)
        assertEquals(PetEventType.PET_RETURNED, dao.events.last().type)
        assertTrue(back.bubble?.line in pool("pet_returned"))
    }

    @Test
    fun D88_obstructive_showsTheBreakScreen_insteadOfTheBubbleAndN02() = runTest {
        val prefs = ReactionPrefs(mode = BreakMode.OBSTRUCTIVE)

        val due = reactions.afterTick(inChrome(continuousMinutes = 45), config, null, petOnScreen = true, prefs = prefs)

        val screen = due.breakScreen as BreakScreenChange.Show
        assertTrue(screen.canSnooze)
        assertEquals(10, screen.buttonDelaySeconds)
        assertEquals(45 * 60_000L, screen.continuousMs)
        assertNull(due.breakNotification)
        assertNull(due.bubble)
    }

    @Test
    fun D89_inACall_obstructiveFallsBackToGentle() = runTest {
        val prefs = ReactionPrefs(mode = BreakMode.OBSTRUCTIVE)

        val due = reactions.afterTick(inChrome(continuousMinutes = 45), config, null, petOnScreen = true, prefs = prefs, calls = { true })

        assertNull(due.breakScreen)
        assertNotNull(due.breakNotification)
    }

    @Test
    fun D98_anAnswer_closesTheScreen_untilItsDueAgain() = runTest {
        val prefs = ReactionPrefs(mode = BreakMode.OBSTRUCTIVE)
        reactions.afterTick(inChrome(continuousMinutes = 45), config, null, petOnScreen = true, prefs = prefs)

        reactions.answerBreakScreen(takeBreak = false, config)
        later(4)
        val stillAway = reactions.afterTick(inChrome(continuousMinutes = 49), config, null, petOnScreen = true, prefs = prefs)
        later(1)
        val back = reactions.afterTick(inChrome(continuousMinutes = 50), config, null, petOnScreen = true, prefs = prefs)

        assertNull(stillAway.breakScreen)
        assertTrue(back.breakScreen is BreakScreenChange.Show)
    }

    @Test
    fun US27_anIdleLine_after30MinutesOfQuiet_andNotWhenSwitchedOff() = runTest {
        reactions.afterTick(inChrome(continuousMinutes = 0, neutralMinutes = 10), config, null, petOnScreen = true)
        later(30)
        val idle = reactions.afterTick(inChrome(continuousMinutes = 30, neutralMinutes = 40), config, null, petOnScreen = true)
        val off = FakePetEventDao().let { log -> Reactions(log, clock, TestLabels, FocusSessions(FakeFocusSessionDao(log), clock)) }
        off.afterTick(inChrome(continuousMinutes = 0, neutralMinutes = 10), config, null, true, prefs = ReactionPrefs(idleEnabled = false))
        val quiet = off.afterTick(inChrome(continuousMinutes = 30, neutralMinutes = 40), config, null, true, prefs = ReactionPrefs(idleEnabled = false))

        assertTrue(idle.bubble?.line in pool("idle"))
        assertNull(quiet.bubble)
    }

    @Test
    fun BUG03_anIdleLineDueWithANag_isDropped_notShownAfterIt() = runTest {
        reactions.afterTick(inInstagram(socialMinutes = 30), config, null, petOnScreen = true)
        later(30)
        val nag = reactions.afterTick(inInstagram(socialMinutes = 60), config, null, petOnScreen = true)
        later(2)
        val after = reactions.afterTick(inInstagram(socialMinutes = 62), config, null, petOnScreen = true)

        assertEquals("limit:social", nag.bubble?.snoozeKey)
        assertNull(after.bubble)
    }

    @Test
    fun theRingStatus_carriesContinuousUse_theBreakLimit_andTheStretch() = runTest {
        val reaction = reactions.afterTick(inChrome(continuousMinutes = 12), config, null, petOnScreen = true)

        assertEquals(12 * 60_000L, reaction.status.continuousMs)
        assertEquals(45, reaction.status.breakAfterMinutes)
        assertEquals(30, reaction.status.stretchLimitMinutes)
    }
}
