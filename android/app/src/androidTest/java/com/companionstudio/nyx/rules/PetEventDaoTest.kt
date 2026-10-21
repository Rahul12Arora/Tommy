package com.companionstudio.nyx.rules

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.companionstudio.nyx.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetEventDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()
    private val dao = database.petEventDao()

    private fun event(day: String, atMs: Long, type: PetEventType) = PetEvent(day = day, atMs = atMs, type = type)

    @After
    fun close() = database.close()

    @Test
    fun D79_eventsComeBackInLogOrder_evenWhenTheClockWentBack() = runBlocking {
        dao.insertAll(listOf(event("2026-10-21", 5_000, PetEventType.DOOMSCROLL_10MIN), event("2026-10-21", 5_000, PetEventType.RAN_AWAY)))
        dao.insertAll(listOf(event("2026-10-21", 1_000, PetEventType.BREAK_TAKEN))) // logged later, with an earlier time
        dao.insertAll(listOf(event("2026-10-20", 9_000, PetEventType.GOAL_MET)))

        assertEquals(
            listOf(PetEventType.DOOMSCROLL_10MIN, PetEventType.RAN_AWAY, PetEventType.BREAK_TAKEN),
            dao.eventsOn("2026-10-21").map { it.type },
        )
        assertEquals(dao.eventsOn("2026-10-21"), dao.observeDay("2026-10-21").first())
    }

    @Test
    fun D93_theSubject_isStoredAndReadBack() = runBlocking {
        dao.insertAll(listOf(PetEvent(day = "2026-10-21", atMs = 1, type = PetEventType.LIMIT_EXCEEDED, subject = "limit:social")))
        dao.insertAll(listOf(event("2026-10-21", 2, PetEventType.BREAK_TAKEN)))

        assertEquals(listOf("limit:social", null), dao.eventsOn("2026-10-21").map { it.subject })
    }

    @Test
    fun observeDay_emitsAfterAnInsert() = runBlocking {
        assertEquals(emptyList<PetEvent>(), dao.observeDay("2026-10-21").first())

        dao.insertAll(listOf(event("2026-10-21", 1, PetEventType.GOAL_MET)))

        assertEquals(listOf(PetEventType.GOAL_MET), dao.observeDay("2026-10-21").first().map { it.type })
    }

    @Test
    fun A13_deleteBefore_keepsThatDayAndLater_andClearAllEmptiesTheLog() = runBlocking {
        dao.insertAll(
            listOf(
                event("2026-09-19", 1, PetEventType.SNOOZED),
                event("2026-09-20", 1, PetEventType.SNOOZED),
                event("2026-10-21", 1, PetEventType.SNOOZED),
            ),
        )

        dao.deleteBefore("2026-09-20") // today 2026-10-21 minus 31 days

        assertEquals(0, dao.eventsOn("2026-09-19").size)
        assertEquals(1, dao.eventsOn("2026-09-20").size)
        assertEquals(1, dao.eventsOn("2026-10-21").size)

        dao.clearAll()
        assertEquals(0, dao.eventsOn("2026-10-21").size)
    }
}
