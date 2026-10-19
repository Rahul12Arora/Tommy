package com.companionstudio.nyx.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.companionstudio.nyx.BuildConfig
import com.companionstudio.nyx.tracking.AppCategoryDao
import com.companionstudio.nyx.tracking.AppCategoryEntity

/** The one Room database. Entities and DAOs live in their feature packages (§14.1). */
@Database(entities = [AppCategoryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appCategoryDao(): AppCategoryDao

    companion object {
        private const val FILE_NAME = "nyx.db"

        fun create(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
            // Until the first Play release, a changed schema wipes debug data instead of crashing.
            // Release builds never do this; from v1.0.0 every change ships a migration (§14.1)
            if (BuildConfig.DEBUG) builder.fallbackToDestructiveMigration(dropAllTables = true)
            return builder.build()
        }
    }
}
