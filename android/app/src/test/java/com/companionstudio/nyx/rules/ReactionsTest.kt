package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.tracking.TrackerSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class ReactionsTest {

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
    private val config = EffectiveConfig(defaults, emptyMap())
    private val today = LocalDate.parse("2026-10-21")
    private val dao = FakePetEventDao()
    private val reactions = Reactions(dao, Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC), Random(5))

    private fun snapshot(socialMinutes: Long, day: LocalDate = today) = TrackerSnapshot(
        day = day,
        frontPackage = "com.instagram.android",
        frontCategory = Category.SOCIAL,
        stretchMs = 0,
        todayMs = mapOf(Category.SOCIAL to socialMinutes * 60_000, Category.PRODUCTIVE to 99 * 60_000),
    )

    private fun loggedTypes() = dao.events.map { it.type }

    private fun linesFor(personality: String, trigger: String) = defaults.lines.getValue(personality).getValue(trigger).getValue("any")

    @Test
    fun D81_doomscrollEvents_areLoggedOnce_asTheTotalGrows() = runTest {
        reactions.afterTick(snapshot(socialMinutes = 25), config, personalityId = null, petOnScreen = true)
        reactions.afterTick(snapshot(socialMinutes = 25), config, personalityId = null, petOnScreen = true)
        assertEquals(List(2) { PetEventType.DOOMSCROLL_10MIN }, loggedTypes())

        val reaction = reactions.afterTick(snapshot(socialMinutes = 31), config, personalityId = null, petOnScreen = true)
        assertEquals(List(3) { PetEventType.DOOMSCROLL_10MIN }, loggedTypes())
        assertEquals(61, reaction.mood)
        assertTrue(dao.events.all { it.day == "2026-10-21" && it.atMs == Instant.parse("2026-10-21T10:00:00Z").toEpochMilli() })
    }

    @Test
    fun US16_walkingTo0_logsRanAway_once_andSaysNothing() = runTest {
        val reaction = reactions.afterTick(snapshot(socialMinutes = 240), config, personalityId = null, petOnScreen = true)
        reactions.afterTick(snapshot(socialMinutes = 250), config, personalityId = null, petOnScreen = true)

        assertEquals(0, reaction.mood)
        assertNull(reaction.bubble)
        assertEquals(List(24) { PetEventType.DOOMSCROLL_10MIN } + PetEventType.RAN_AWAY + PetEventType.DOOMSCROLL_10MIN, loggedTypes())
    }

    @Test
    fun US16_aPositiveEvent_bringsThePetBack_withAReturnLineInTheChosenPersonality() = runTest {
        reactions.afterTick(snapshot(socialMinutes = 240), config, personalityId = "calm", petOnScreen = true)
        dao.log(PetEventType.BREAK_TAKEN)

        val back = reactions.afterTick(snapshot(socialMinutes = 240), config, personalityId = "calm", petOnScreen = true)
        val after = reactions.afterTick(snapshot(socialMinutes = 240), config, personalityId = "calm", petOnScreen = true)

        assertEquals(5, back.mood)
        assertTrue(back.bubble in linesFor("calm", "pet_returned"))
        assertEquals(PetEventType.PET_RETURNED, loggedTypes().last())
        assertEquals(1, loggedTypes().count { it == PetEventType.PET_RETURNED })
        assertNull(after.bubble)
    }

    @Test
    fun D24_petNotOnScreen_noBubble_butTheReturnIsStillLogged_andNotRepeatedLater() = runTest {
        reactions.afterTick(snapshot(socialMinutes = 240), config, personalityId = null, petOnScreen = false)
        dao.log(PetEventType.BREAK_TAKEN)

        val hidden = reactions.afterTick(snapshot(socialMinutes = 240), config, personalityId = null, petOnScreen = false)
        val shownAgain = reactions.afterTick(snapshot(socialMinutes = 240), config, personalityId = null, petOnScreen = true)

        assertNull(hidden.bubble)
        assertNull(shownAgain.bubble)
        assertEquals(PetEventType.PET_RETURNED, loggedTypes().last())
    }

    @Test
    fun aDisabledPersonality_speaksAsTheFirstEnabledOne() = runTest {
        val calmOff = EffectiveConfig(
            defaults.copy(personalities = defaults.personalities.map { if (it.id == "calm") it.copy(enabled = false) else it }),
            emptyMap(),
        )
        reactions.afterTick(snapshot(socialMinutes = 240), calmOff, personalityId = "calm", petOnScreen = true)
        dao.log(PetEventType.GOAL_MET)

        val back = reactions.afterTick(snapshot(socialMinutes = 240), calmOff, personalityId = "calm", petOnScreen = true)

        assertTrue(back.bubble in linesFor("cheerful", "pet_returned"))
    }

    @Test
    fun A13_oldDaysAreDeleted_onceADay() = runTest {
        reactions.afterTick(snapshot(socialMinutes = 0), config, personalityId = null, petOnScreen = true)
        reactions.afterTick(snapshot(socialMinutes = 0), config, personalityId = null, petOnScreen = true)
        reactions.afterTick(snapshot(socialMinutes = 0, day = today.plusDays(1)), config, personalityId = null, petOnScreen = true)

        assertEquals(listOf("2026-09-20", "2026-09-21"), dao.deletedBefore)
    }

    @Test
    fun US15_aNewDay_startsFromTheStartingMood() = runTest {
        reactions.afterTick(snapshot(socialMinutes = 240), config, personalityId = null, petOnScreen = true)

        val tomorrow = reactions.afterTick(snapshot(socialMinutes = 0, day = today.plusDays(1)), config, personalityId = null, petOnScreen = true)

        assertEquals(70, tomorrow.mood)
    }

    private class FakePetEventDao : PetEventDao {
        val events = mutableListOf<PetEvent>()
        val deletedBefore = mutableListOf<String>()

        fun log(type: PetEventType) {
            events += PetEvent(id = events.size + 1L, day = "2026-10-21", atMs = 0, type = type)
        }

        override suspend fun insertAll(events: List<PetEvent>) {
            events.forEach { this.events += it.copy(id = this.events.size + 1L) }
        }

        // Insertion order, like the real DAO's id order
        override suspend fun eventsOn(day: String) = events.filter { it.day == day }

        override fun observeDay(day: String): Flow<List<PetEvent>> = flowOf(events.filter { it.day == day })

        override suspend fun deleteBefore(day: String) {
            deletedBefore += day
        }

        override suspend fun clearAll() = events.clear()
    }
}
