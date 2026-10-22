package com.companionstudio.nyx.focus

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.companionstudio.nyx.rules.PetEvent
import com.companionstudio.nyx.rules.PetEventType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FocusSessionDao {

    @Query("SELECT * FROM focus_sessions WHERE outcome IS NULL LIMIT 1")
    abstract fun observeActive(): Flow<FocusSession?>

    @Query("SELECT * FROM focus_sessions WHERE outcome IS NULL LIMIT 1")
    abstract suspend fun active(): FocusSession?

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    abstract suspend fun byId(id: Long): FocusSession?

    /** The most recent session, for S-09's defaults (D-117). */
    @Query("SELECT * FROM focus_sessions ORDER BY id DESC LIMIT 1")
    abstract suspend fun latest(): FocusSession?

    /** Inserts [session] unless one is already active (one at a time, SM-04). Returns its id, or `null`. */
    @Transaction
    open suspend fun start(session: FocusSession): Long? {
        if (active() != null) return null
        return insert(session)
    }

    @Query("UPDATE focus_sessions SET focus_ms = :focusMs, switches = :switches, last_focus_at_ms = :lastFocusAtMs WHERE id = :id AND outcome IS NULL")
    abstract suspend fun saveProgress(id: Long, focusMs: Long, switches: Int, lastFocusAtMs: Long)

    /**
     * Ends session [id] if it's still active, and logs `FOCUS_COMPLETED` on [eventDay] in the same transaction when it
     * completed (D-109). Returns `false` if it had already ended, so the ring, Home and the engine can't reward twice.
     */
    @Transaction
    open suspend fun finish(id: Long, outcome: FocusOutcome, endedAtMs: Long, eventDay: String): Boolean {
        if (markEnded(id, outcome.name, endedAtMs) == 0) return false
        if (outcome == FocusOutcome.COMPLETED) {
            insertEvent(PetEvent(day = eventDay, atMs = endedAtMs, type = PetEventType.FOCUS_COMPLETED, subject = id.toString()))
        }
        return true
    }

    /** Retention (A-13), like the event log. An active session is kept whatever its day. */
    @Query("DELETE FROM focus_sessions WHERE day < :day AND outcome IS NOT NULL")
    abstract suspend fun deleteBefore(day: String)

    /** Every session. Only the debug scenarios use it (D-119). */
    @Query("DELETE FROM focus_sessions")
    abstract suspend fun clearAll()

    @Insert
    protected abstract suspend fun insert(session: FocusSession): Long

    @Query("UPDATE focus_sessions SET outcome = :outcome, ended_at_ms = :endedAtMs WHERE id = :id AND outcome IS NULL")
    protected abstract suspend fun markEnded(id: Long, outcome: String, endedAtMs: Long): Int

    @Insert
    protected abstract suspend fun insertEvent(event: PetEvent)
}
