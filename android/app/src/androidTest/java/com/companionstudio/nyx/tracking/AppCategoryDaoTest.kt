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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCategoryDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()
    private val dao = database.appCategoryDao()

    @After
    fun close() = database.close()

    @Test
    fun upsert_insertsThenReplaces() = runBlocking {
        dao.upsert(AppCategoryEntity("com.instagram.android", Category.SOCIAL))
        dao.upsert(AppCategoryEntity("com.example.game", Category.GAMES))
        dao.upsert(AppCategoryEntity("com.instagram.android", Category.PRODUCTIVE))

        assertEquals(
            setOf(
                AppCategoryEntity("com.instagram.android", Category.PRODUCTIVE),
                AppCategoryEntity("com.example.game", Category.GAMES),
            ),
            dao.observeAll().first().toSet(),
        )
    }

    @Test
    fun observeAll_emitsAfterAChange() = runBlocking {
        assertEquals(emptyList<AppCategoryEntity>(), dao.observeAll().first())

        dao.upsert(AppCategoryEntity("com.example.game", Category.GAMES))

        assertEquals(listOf(AppCategoryEntity("com.example.game", Category.GAMES)), dao.observeAll().first())
    }
}
