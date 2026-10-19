package com.companionstudio.nyx.tracking

import com.companionstudio.nyx.config.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The categories the user chose, by package (D-58). Apps without a choice use [suggestCategory]. */
class AppCategoryRepository(private val dao: AppCategoryDao) {

    fun observeChoices(): Flow<Map<String, Category>> =
        dao.observeAll().map { rows -> rows.associate { it.packageName to it.category } }

    suspend fun choose(packageName: String, category: Category) {
        dao.upsert(AppCategoryEntity(packageName, category))
    }
}
