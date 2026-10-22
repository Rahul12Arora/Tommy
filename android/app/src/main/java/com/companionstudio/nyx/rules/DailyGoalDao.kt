package com.companionstudio.nyx.rules

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DailyGoalDao {

    /** [day]'s goals become exactly [goals], all or nothing. */
    @Transaction
    open suspend fun replaceDay(day: String, goals: List<DailyGoal>) {
        deleteDay(day)
        insertAll(goals)
    }

    @Query("SELECT * FROM daily_goals WHERE day >= :from")
    abstract fun observeSince(from: String): Flow<List<DailyGoal>>

    /** The first day Nyx was tracking (D-151): the engine writes goals on the first tick of every day. */
    @Query("SELECT MIN(day) FROM (SELECT day FROM daily_app_usage UNION ALL SELECT day FROM daily_goals)")
    abstract fun observeFirstDay(): Flow<String?>

    /** Retention (A-13): day keys are ISO dates, so comparing them as text compares the dates. */
    @Query("DELETE FROM daily_goals WHERE day < :day")
    abstract suspend fun deleteBefore(day: String)

    /** Every row. Only the debug scenarios and seed use it (D-155). */
    @Query("DELETE FROM daily_goals")
    abstract suspend fun clearAll()

    @Insert
    abstract suspend fun insertAll(goals: List<DailyGoal>)

    @Query("DELETE FROM daily_goals WHERE day = :day")
    protected abstract suspend fun deleteDay(day: String)
}
