package com.companionstudio.nyx.tracking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DailyUsageDao {

    /** Adds [credits] to the totals and moves the cursor, all or nothing, so time is never lost or counted twice. */
    @Transaction
    open suspend fun record(credits: List<DailyUsage>, creditedUntilMs: Long) {
        for (credit in credits) {
            if (addTo(credit.day, credit.packageName, credit.category.name, credit.durationMs) == 0) insert(credit)
        }
        setCursor(TrackingCursor(creditedUntilMs = creditedUntilMs))
    }

    @Query("SELECT credited_until_ms FROM tracking_cursor WHERE id = 0")
    abstract suspend fun creditedUntilMs(): Long?

    @Query("SELECT * FROM daily_app_usage WHERE day = :day")
    abstract fun observeDay(day: String): Flow<List<DailyUsage>>

    @Query("SELECT category, SUM(duration_ms) AS durationMs FROM daily_app_usage WHERE day = :day GROUP BY category")
    abstract suspend fun categoryTotals(day: String): List<CategoryTotal>

    /** Retention (A-13): day keys are ISO dates, so comparing them as text compares the dates. */
    @Query("DELETE FROM daily_app_usage WHERE day < :day")
    abstract suspend fun deleteBefore(day: String)

    /** All tracked time and the cursor. Only the debug scenarios use it (D-67). */
    @Transaction
    open suspend fun clearAll() {
        deleteAllUsage()
        deleteCursor()
    }

    @Query("UPDATE daily_app_usage SET duration_ms = duration_ms + :durationMs WHERE day = :day AND package_name = :packageName AND category = :category")
    protected abstract suspend fun addTo(day: String, packageName: String, category: String, durationMs: Long): Int

    @Insert
    protected abstract suspend fun insert(row: DailyUsage)

    @Upsert
    protected abstract suspend fun setCursor(cursor: TrackingCursor)

    @Query("DELETE FROM daily_app_usage")
    protected abstract suspend fun deleteAllUsage()

    @Query("DELETE FROM tracking_cursor")
    protected abstract suspend fun deleteCursor()
}
