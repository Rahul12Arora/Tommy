package com.companionstudio.nyx.focus

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.companionstudio.nyx.data.AppDatabase
import com.companionstudio.nyx.rules.PetEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusSessionDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()
    private val dao = database.focusSessionDao()
    private val events = database.petEventDao()

    private fun session(day: String = "2026-10-21", apps: List<String> = listOf("com.google.android.youtube", "org.wikipedia")) = FocusSession(
        day = day,
        startedAtMs = 1_000,
        goal = FocusGoal.FIXED_TIME,
        goalMinutes = 25,
        allowedApps = apps,
        petMode = PetMode.CHECK_IN,
        checkinMinutes = 10,
    )

    @After
    fun close() = database.close()

    @Test
    fun SM04_oneAtATime_aSecondStartWritesNothing() = runBlocking {
        val id = dao.start(session())

        assertNotNull(id)
        assertNull(dao.start(session()))
        assertEquals(id, dao.active()?.id)
    }

    @Test
    fun D109_finishOnlyOnce_andACompletedSessionLogsFocusCompletedWithIt() = runBlocking {
        val id = checkNotNull(dao.start(session()))

        assertTrue(dao.finish(id, FocusOutcome.COMPLETED, endedAtMs = 9_000, eventDay = "2026-10-22"))
        assertFalse(dao.finish(id, FocusOutcome.ENDED_EARLY, endedAtMs = 9_500, eventDay = "2026-10-22"))

        val ended = checkNotNull(dao.byId(id))
        assertEquals(FocusOutcome.COMPLETED, ended.outcome)
        assertEquals(9_000L, ended.endedAtMs)
        assertNull(dao.active())
        val logged = events.eventsOn("2026-10-22")
        assertEquals(listOf(PetEventType.FOCUS_COMPLETED), logged.map { it.type })
        assertEquals(id.toString(), logged.single().subject)
    }

    @Test
    fun US14_otherOutcomes_logNothing() = runBlocking {
        val broken = checkNotNull(dao.start(session()))
        dao.finish(broken, FocusOutcome.BROKEN, 2_000, "2026-10-21")
        val early = checkNotNull(dao.start(session()))
        dao.finish(early, FocusOutcome.ENDED_EARLY, 3_000, "2026-10-21")

        assertTrue(events.eventsOn("2026-10-21").isEmpty())
    }

    @Test
    fun progressIsSaved_onlyWhileActive_andTheAppsListRoundTrips() = runBlocking {
        val id = checkNotNull(dao.start(session()))
        dao.saveProgress(id, focusMs = 60_000, switches = 2, lastFocusAtMs = 5_000)
        dao.finish(id, FocusOutcome.ENDED_EARLY, 6_000, "2026-10-21")
        dao.saveProgress(id, focusMs = 99_000, switches = 9, lastFocusAtMs = 7_000)

        val saved = checkNotNull(dao.latest())
        assertEquals(60_000L, saved.focusMs)
        assertEquals(2, saved.switches)
        assertEquals(listOf("com.google.android.youtube", "org.wikipedia"), saved.allowedApps)
    }

    @Test
    fun observeActive_emitsOnStartAndEnd() = runBlocking {
        assertNull(dao.observeActive().first())
        val id = checkNotNull(dao.start(session()))
        assertEquals(id, dao.observeActive().first()?.id)
        dao.finish(id, FocusOutcome.ENDED_EARLY, 2_000, "2026-10-21")
        assertNull(dao.observeActive().first())
    }

    @Test
    fun A13_deleteBefore_keepsTheCutOffDay_andAnActiveSession() = runBlocking {
        val old = checkNotNull(dao.start(session(day = "2026-09-01")))
        dao.finish(old, FocusOutcome.COMPLETED, 2_000, "2026-09-01")
        val kept = checkNotNull(dao.start(session(day = "2026-09-20")))
        dao.finish(kept, FocusOutcome.COMPLETED, 2_000, "2026-09-20")
        val activeOld = checkNotNull(dao.start(session(day = "2026-09-02")))

        dao.deleteBefore("2026-09-20")

        assertNull(dao.byId(old))
        assertNotNull(dao.byId(kept))
        assertNotNull(dao.byId(activeOld))
    }
}
