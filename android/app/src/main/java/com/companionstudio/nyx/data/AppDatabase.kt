package com.companionstudio.nyx.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.companionstudio.nyx.BuildConfig
import com.companionstudio.nyx.focus.FocusSession
import com.companionstudio.nyx.focus.FocusSessionDao
import com.companionstudio.nyx.rules.DailyGoal
import com.companionstudio.nyx.rules.DailyGoalDao
import com.companionstudio.nyx.rules.PetEvent
import com.companionstudio.nyx.rules.PetEventDao
import com.companionstudio.nyx.tracking.AppCategoryDao
import com.companionstudio.nyx.tracking.AppCategoryEntity
import com.companionstudio.nyx.tracking.DailyUsage
import com.companionstudio.nyx.tracking.DailyUsageDao
import com.companionstudio.nyx.tracking.TrackingCursor

/** The one Room database. Entities and DAOs live in their feature packages (§14.1). */
@Database(
    entities = [AppCategoryEntity::class, DailyUsage::class, TrackingCursor::class, PetEvent::class, FocusSession::class, DailyGoal::class],
    version = 6,
    exportSchema = true,
    // Version 3 only adds the event log (D-85), version 4 only a nullable column to it (D-93), version 5 only the
    // focus sessions (§14.8), version 6 only the daily goals (§14.13), so debug installs keep their categories and totals
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appCategoryDao(): AppCategoryDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun petEventDao(): PetEventDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun dailyGoalDao(): DailyGoalDao

    companion object {
        private const val FILE_NAME = "nyx.db"

        fun create(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
            // Until the first Play release, a changed schema without a migration wipes debug data instead of crashing.
            // Release builds never do this; from v1.0.0 every change ships a migration (§14.1)
            if (BuildConfig.DEBUG) builder.fallbackToDestructiveMigration(dropAllTables = true)
            return builder.build()
        }
    }
}
