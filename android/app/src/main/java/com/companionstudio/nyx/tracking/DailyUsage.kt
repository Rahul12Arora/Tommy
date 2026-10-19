package com.companionstudio.nyx.tracking

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.companionstudio.nyx.config.Category

/**
 * Time spent in one app on one day, under the category it had at the time (US-04).
 * [day] is the ISO day key; time is kept in milliseconds so short segments don't round away (D-63).
 */
@Entity(tableName = "daily_app_usage", primaryKeys = ["day", "package_name", "category"])
data class DailyUsage(
    val day: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    val category: Category,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

/** The one row saying how far usage has been credited (D-62). Written in the same transaction as the totals. */
@Entity(tableName = "tracking_cursor")
data class TrackingCursor(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "credited_until_ms") val creditedUntilMs: Long,
)

/** One category's total for a day. */
data class CategoryTotal(val category: Category, val durationMs: Long)
