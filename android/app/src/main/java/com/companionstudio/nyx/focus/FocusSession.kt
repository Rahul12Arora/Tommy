package com.companionstudio.nyx.focus

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/** What finishes a session (US-12, §14.8). Room stores these enums by their Kotlin names, like [com.companionstudio.nyx.config.Category]. */
enum class FocusGoal {
    /** Completes when focus time reaches the minutes. */
    FIXED_TIME,

    /** Completes when the user taps Done (A-01). */
    UNTIL_DONE,

    /** Like [FIXED_TIME], but the first switch breaks it. */
    NO_SWITCHING,
}

/** How chatty the pet is during a session (D-112). */
enum class PetMode { QUIET, REWARD_AT_END, CHECK_IN }

enum class FocusOutcome { COMPLETED, BROKEN, ENDED_EARLY }

/**
 * One focus session (SM-04, D-109). [outcome] `null` = still active; there's at most one. [day] is the day it started
 * (A-25). [focusMs], [switches] and [lastFocusAtMs] are saved by the engine every tick; the live phase isn't stored.
 */
@Entity(tableName = "focus_sessions", indices = [Index("day")])
@TypeConverters(AllowedAppsConverter::class)
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    val goal: FocusGoal,
    /** `null` for [FocusGoal.UNTIL_DONE]. */
    @ColumnInfo(name = "goal_minutes") val goalMinutes: Int?,
    @ColumnInfo(name = "allowed_apps") val allowedApps: List<String>,
    @ColumnInfo(name = "pet_mode") val petMode: PetMode,
    /** Only for [PetMode.CHECK_IN]. */
    @ColumnInfo(name = "checkin_minutes") val checkinMinutes: Int?,
    @ColumnInfo(name = "focus_ms") val focusMs: Long = 0,
    val switches: Int = 0,
    /** The last moment focus time grew; the start at first (D-114). */
    @ColumnInfo(name = "last_focus_at_ms") val lastFocusAtMs: Long = startedAtMs,
    val outcome: FocusOutcome? = null,
    @ColumnInfo(name = "ended_at_ms") val endedAtMs: Long? = null,
)

/** Package names can't contain commas, so a comma-joined list is enough. */
class AllowedAppsConverter {
    @TypeConverter
    fun toText(apps: List<String>): String = apps.joinToString(",")

    @TypeConverter
    fun toApps(text: String): List<String> = if (text.isEmpty()) emptyList() else text.split(",")
}
