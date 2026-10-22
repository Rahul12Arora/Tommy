package com.companionstudio.nyx.rules

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PetEventDao {
    @Insert
    suspend fun insertAll(events: List<PetEvent>)

    /**
     * In the order they were logged, which is the order mood adds them up in (D-79). By id, not time: a clock set
     * back (or the debug fast clock stopping) must not move new events before old ones.
     */
    @Query("SELECT * FROM events WHERE day = :day ORDER BY id")
    suspend fun eventsOn(day: String): List<PetEvent>

    @Query("SELECT * FROM events WHERE day = :day ORDER BY id")
    fun observeDay(day: String): Flow<List<PetEvent>>

    /** What Trends judges goals by (D-149): limits exceeded and positive goals met, from [from] on. */
    @Query("SELECT * FROM events WHERE day >= :from AND type IN ('LIMIT_EXCEEDED', 'GOAL_MET')")
    fun observeGoalEventsSince(from: String): Flow<List<PetEvent>>

    /** Retention (A-13): day keys are ISO dates, so comparing them as text compares the dates. */
    @Query("DELETE FROM events WHERE day < :day")
    suspend fun deleteBefore(day: String)

    /** The whole log. Only the debug scenarios use it (D-67). */
    @Query("DELETE FROM events")
    suspend fun clearAll()
}
