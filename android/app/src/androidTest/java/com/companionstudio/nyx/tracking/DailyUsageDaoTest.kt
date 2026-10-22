package com.companionstudio.nyx.tracking

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyUsageDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()
    private val dao = database.dailyUsageDao()

    private fun usage(day: String, packageName: String, category: Category, durationMs: Long) =
        DailyUsage(day, packageName, category, durationMs)

    @After
    fun close() = database.close()

    @Test
    fun record_addsToExistingTotals_andMovesTheCursor() = runBlocking {
        dao.record(listOf(usage("2026-10-21", "com.instagram.android", Category.SOCIAL, 30_000)), creditedUntilMs = 1_000)
        dao.record(
            listOf(
                usage("2026-10-21", "com.instagram.android", Category.SOCIAL, 15_500),
                usage("2026-10-21", "com.instagram.android", Category.PRODUCTIVE, 1_000), // category changed mid-day
            ),
            creditedUntilMs = 2_000,
        )

        assertEquals(
            setOf(
                usage("2026-10-21", "com.instagram.android", Category.SOCIAL, 45_500),
                usage("2026-10-21", "com.instagram.android", Category.PRODUCTIVE, 1_000),
            ),
            dao.observeDay("2026-10-21").first().toSet(),
        )
        assertEquals(2_000L, dao.creditedUntilMs())
    }

    @Test
    fun categoryTotals_sumAppsPerCategory_forThatDayOnly() = runBlocking {
        dao.record(
            listOf(
                usage("2026-10-21", "com.instagram.android", Category.SOCIAL, 60_000),
                usage("2026-10-21", "com.zhiliaoapp.musically", Category.SOCIAL, 30_000),
                usage("2026-10-21", "com.google.android.youtube", Category.ENTERTAINMENT, 10_000),
                usage("2026-10-20", "com.instagram.android", Category.SOCIAL, 99_000),
            ),
            creditedUntilMs = 1,
        )

        assertEquals(
            setOf(CategoryTotal(Category.SOCIAL, 90_000), CategoryTotal(Category.ENTERTAINMENT, 10_000)),
            dao.categoryTotals("2026-10-21").toSet(),
        )
    }

    @Test
    fun A13_deleteBefore_keepsThatDayAndLater() = runBlocking {
        dao.record(
            listOf(
                usage("2026-09-19", "com.example.a", Category.NEUTRAL, 1),
                usage("2026-09-20", "com.example.a", Category.NEUTRAL, 1),
                usage("2026-10-21", "com.example.a", Category.NEUTRAL, 1),
            ),
            creditedUntilMs = 1,
        )

        dao.deleteBefore("2026-09-20") // today 2026-10-21 minus 31 days

        assertEquals(emptyList<DailyUsage>(), dao.observeDay("2026-09-19").first())
        assertEquals(1, dao.observeDay("2026-09-20").first().size)
        assertEquals(1, dao.observeDay("2026-10-21").first().size)
    }

    @Test
    fun observeDay_emitsAfterAChange_andClearAllEmptiesEverything() = runBlocking {
        assertEquals(emptyList<DailyUsage>(), dao.observeDay("2026-10-21").first())

        dao.record(listOf(usage("2026-10-21", "com.example.a", Category.NEUTRAL, 1_000)), creditedUntilMs = 5)
        assertEquals(1, dao.observeDay("2026-10-21").first().size)

        dao.clearAll()
        assertEquals(emptyList<DailyUsage>(), dao.observeDay("2026-10-21").first())
        assertNull(dao.creditedUntilMs())
    }

    @Test
    fun US18_observeTotalsSince_groupsByDayAndCategory_fromThatDayOn() = runBlocking {
        dao.record(
            listOf(
                usage("2026-10-20", "com.instagram.android", Category.SOCIAL, 9_000),
                usage("2026-10-21", "com.instagram.android", Category.SOCIAL, 1_000),
                usage("2026-10-21", "com.zhiliaoapp.musically", Category.SOCIAL, 2_000),
                usage("2026-10-21", "com.google.android.apps.docs", Category.PRODUCTIVE, 4_000),
            ),
            creditedUntilMs = 0,
        )

        assertEquals(
            setOf(DayCategoryTotal("2026-10-21", Category.SOCIAL, 3_000), DayCategoryTotal("2026-10-21", Category.PRODUCTIVE, 4_000)),
            dao.observeTotalsSince("2026-10-21").first().toSet(),
        )
    }
}
