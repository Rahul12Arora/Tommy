package com.companionstudio.nyx.home

/** "3 h 12 min", "1 h", "47 min". Partial minutes are dropped. English only in v1. */
fun formatDuration(seconds: Long): String {
    require(seconds >= 0) { "seconds must not be negative, was $seconds" }
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours h"
        else -> "$hours h $minutes min"
    }
}
