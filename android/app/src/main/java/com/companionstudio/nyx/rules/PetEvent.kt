package com.companionstudio.nyx.rules

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What the engine logs (§14.6). Mood is recomputed from today's events (H-03), so these are the facts it's made of.
 * Room stores the type by its Kotlin name, so renaming a constant needs a migration.
 */
enum class PetEventType {
    // The six with a mood weight (SM-01). M6 writes DOOMSCROLL_10MIN; M7, M8 and M13 write the others
    DOOMSCROLL_10MIN,
    LIMIT_EXCEEDED,
    SNOOZED,
    BREAK_TAKEN,
    GOAL_MET,
    FOCUS_COMPLETED,

    // Weigh nothing: they record that the pet ran away and came back (US-16, D-80)
    RAN_AWAY,
    PET_RETURNED,
}

/** One logged event. [day] is the ISO day key of the moment it was logged; [id] is its place in the log. */
@Entity(tableName = "events", indices = [Index("day")])
data class PetEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    @ColumnInfo(name = "at_ms") val atMs: Long,
    val type: PetEventType,
)
