package com.companionstudio.nyx.tracking

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppCategoryDao {
    @Query("SELECT * FROM app_categories")
    fun observeAll(): Flow<List<AppCategoryEntity>>

    @Upsert
    suspend fun upsert(entity: AppCategoryEntity)
}
