package com.companionstudio.nyx.setup

import android.content.pm.ApplicationInfo
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.tracking.AppCategoryDao
import com.companionstudio.nyx.tracking.AppCategoryEntity
import com.companionstudio.nyx.tracking.AppCategoryRepository
import com.companionstudio.nyx.tracking.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class US04_AppCategoriesViewModelTest {

    private val document = SharedFiles.minimalConfig.copy(
        categoryDefaults = mapOf("com.instagram.android" to Category.SOCIAL),
        androidCategoryMap = mapOf("game" to Category.GAMES),
    )
    private val apps = listOf(
        InstalledApp("com.instagram.android", "Instagram", ApplicationInfo.CATEGORY_UNDEFINED),
        InstalledApp("com.example.puzzle", "Puzzle Quest", ApplicationInfo.CATEGORY_GAME),
        InstalledApp("com.android.settings", "Settings", ApplicationInfo.CATEGORY_UNDEFINED),
    )

    private class FakeDao : AppCategoryDao {
        val rows = MutableStateFlow<List<AppCategoryEntity>>(emptyList())
        override fun observeAll(): Flow<List<AppCategoryEntity>> = rows
        override suspend fun upsert(entity: AppCategoryEntity) {
            rows.value = rows.value.filterNot { it.packageName == entity.packageName } + entity
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun noChoices_everyAppShowsItsSuggestion() {
        assertEquals(
            listOf(
                AppRow("com.instagram.android", "Instagram", Category.SOCIAL),
                AppRow("com.example.puzzle", "Puzzle Quest", Category.GAMES),
                AppRow("com.android.settings", "Settings", Category.NEUTRAL),
            ),
            appRows(apps, choices = emptyMap(), document, query = ""),
        )
    }

    @Test
    fun usersChoice_winsOverTheSuggestion() {
        val rows = appRows(apps, mapOf("com.instagram.android" to Category.PRODUCTIVE), document, query = "")

        assertEquals(Category.PRODUCTIVE, rows.first { it.packageName == "com.instagram.android" }.category)
    }

    @Test
    fun search_matchesAnywhereInTheLabel_ignoringCase_andSurroundingSpaces() {
        assertEquals(listOf("Puzzle Quest"), appRows(apps, emptyMap(), document, query = "  QUEST ").map { it.label })
        assertEquals(apps.size, appRows(apps, emptyMap(), document, query = " ").size)
        assertEquals(emptyList<AppRow>(), appRows(apps, emptyMap(), document, query = "zzz"))
    }

    @Test
    fun choose_isStored_andTheListFollows() = runTest {
        val dao = FakeDao()
        val viewModel = AppCategoriesViewModel({ apps }, flowOf(EffectiveConfig(document, emptyMap())), AppCategoryRepository(dao))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.rows.collect {} }

        viewModel.choose("com.android.settings", Category.PRODUCTIVE)

        assertEquals(listOf(AppCategoryEntity("com.android.settings", Category.PRODUCTIVE)), dao.rows.value)
        assertEquals(Category.PRODUCTIVE, viewModel.rows.value?.first { it.packageName == "com.android.settings" }?.category)
    }

    @Test
    fun search_filtersTheList() = runTest {
        val viewModel = AppCategoriesViewModel({ apps }, flowOf(EffectiveConfig(document, emptyMap())), AppCategoryRepository(FakeDao()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.rows.collect {} }

        viewModel.search("insta")

        assertEquals("insta", viewModel.query.value)
        assertEquals(listOf("Instagram"), viewModel.rows.value?.map { it.label })
    }
}
