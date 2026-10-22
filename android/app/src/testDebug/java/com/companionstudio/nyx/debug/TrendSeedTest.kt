package com.companionstudio.nyx.debug

import com.companionstudio.nyx.home.TrendRange
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.home.trendDays
import com.companionstudio.nyx.rules.GoalKeys
import com.companionstudio.nyx.rules.PetEventType
import com.companionstudio.nyx.tracking.DayCategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The `trends-30d` seed (D-155): fixed, and covering every case S-10 shows. */
class TrendSeedTest {

    private val today = LocalDate.parse("2026-10-23")
    private val seed = trendSeed(today)

    // What S-10 makes of it, with nothing tracked today yet
    private val month = trendDays(
        today,
        TrendRange.MONTH,
        firstDay = seed.goals.minOf { LocalDate.parse(it.day) },
        totals = seed.usage.map { DayCategoryTotal(it.day, it.category, it.durationMs) },
        goals = seed.goals,
        goalEvents = seed.events.filter { it.type == PetEventType.LIMIT_EXCEEDED || it.type == PetEventType.GOAL_MET },
    )

    @Test
    fun D155_isTheSameOnEveryRun() {
        val again = trendSeed(today)

        assertEquals(seed.usage, again.usage)
        assertEquals(seed.goals, again.goals)
        assertEquals(seed.events, again.events)
    }

    @Test
    fun D155_24PastDays_leaveFiveUntrackedDaysInTheMonth_andTodayAlone() {
        assertEquals(SEEDED_DAYS, seed.goals.map { it.day }.distinct().size)
        assertEquals(LocalDate.parse("2026-09-29"), seed.goals.minOf { LocalDate.parse(it.day) })
        assertTrue(seed.usage.none { it.day == today.toString() })
        assertEquals(5, month.count { !it.tracked })
        assertEquals((0 until 5).toList(), month.indices.filter { !month[it].tracked })
    }

    @Test
    fun D155_coversZero_long_kept_missed_met_notMet_andGoalsSwitchedOnMidRange() {
        val seeded = month.filter { it.tracked && !it.isToday }

        assertTrue("a zero day", seeded.any { it.totalSeconds == 0L })
        assertTrue("a day over 8 h", seeded.any { it.totalSeconds > 8 * 3600 })
        val social = seeded.flatMap { day -> day.goals.filter { !it.goal.isPositive } }
        assertTrue("Social kept and missed", social.any { it.achieved } && social.any { !it.achieved })
        val positive = seeded.flatMap { day -> day.goals.filter { it.goal.isPositive } }
        assertTrue("positive met and not met", positive.any { it.achieved } && positive.any { !it.achieved })
        assertTrue("some days have only the limit", seeded.any { it.goals.size == 1 } && seeded.any { it.goals.size == 3 })
        assertTrue("one day with every goal met", seeded.any { it.goals.size == 3 && it.goalsAchieved == 3 })
        assertTrue("one day with none", seeded.any { it.goals.size == 3 && it.goalsAchieved == 0 })
    }

    @Test
    fun D155_theLoggedLines_sayWhatS10Shows() {
        val shown = month.filter { it.tracked && !it.isToday }.map { day ->
            "${day.day}: ${formatDuration(day.totalSeconds)}, goals ${day.goalsAchieved} of ${day.goals.size}"
        }

        assertEquals(shown, seed.lines)
    }

    @Test
    fun D155_goalMetOnlyWhereTheRulesSaySo() {
        val focusMet = seed.events.filter { it.type == PetEventType.GOAL_MET && it.subject == GoalKeys.FOCUS_SESSIONS }.map { it.day }.toSet()
        val twoOrMore = seed.events.filter { it.type == PetEventType.FOCUS_COMPLETED }.groupBy { it.day }
            .filterValues { it.size >= 2 }.keys
            .filter { day -> seed.goals.any { it.day == day && it.goalKey == GoalKeys.FOCUS_SESSIONS } }
            .toSet()

        assertEquals(twoOrMore, focusMet)
    }
}
