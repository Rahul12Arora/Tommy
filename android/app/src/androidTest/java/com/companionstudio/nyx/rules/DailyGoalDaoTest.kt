package com.companionstudio.nyx.rules

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.data.AppDatabase
import com.companionstudio.nyx.tracking.DailyUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyGoalDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()
    private val dao = database.dailyGoalDao()

    @After
    fun close() = database.close()

    @Test
    fun D148_replaceDay_swapsThatDayOnly() = runBlocking {
        dao.replaceDay("2026-10-21", listOf(DailyGoal("2026-10-21", "limit:social", 60)))
        dao.replaceDay("2026-10-22", listOf(DailyGoal("2026-10-22", "limit:social", 60)))
        dao.replaceDay("2026-10-22", listOf(DailyGoal("2026-10-22", "limit:social", 45), DailyGoal("2026-10-22", GoalKeys.FOCUS_SESSIONS, 2)))

        assertEquals(
            setOf(
                DailyGoal("2026-10-21", "limit:social", 60),
                DailyGoal("2026-10-22", "limit:social", 45),
                DailyGoal("2026-10-22", GoalKeys.FOCUS_SESSIONS, 2),
            ),
            dao.observeSince("2026-10-01").first().toSet(),
        )
        assertEquals(listOf("2026-10-22"), dao.observeSince("2026-10-22").first().map { it.day }.distinct())
    }

    @Test
    fun D151_theFirstDay_isTheEarliestDayInEitherTable_nullWhenBothAreEmpty() = runBlocking {
        assertNull(dao.observeFirstDay().first())

        dao.replaceDay("2026-10-20", listOf(DailyGoal("2026-10-20", "limit:social", 60)))
        assertEquals("2026-10-20", dao.observeFirstDay().first())

        database.dailyUsageDao().record(listOf(DailyUsage("2026-10-18", "com.android.chrome", Category.NEUTRAL, 1_000)), creditedUntilMs = 0)
        assertEquals("2026-10-18", dao.observeFirstDay().first())
    }

    @Test
    fun A13_deleteBefore_keepsThatDayAndLater() = runBlocking {
        listOf("2026-09-20", "2026-09-21").forEach { dao.replaceDay(it, listOf(DailyGoal(it, "limit:social", 60))) }

        dao.deleteBefore("2026-09-21")

        assertEquals(listOf("2026-09-21"), dao.observeSince("2026-01-01").first().map { it.day })
    }
}
