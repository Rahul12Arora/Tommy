package com.companionstudio.nyx.config

/**
 * Setting paths: the user's stored values are keyed by these (M3), and features read values with them.
 * Constants so call sites never spell a path by hand.
 */
object SettingPaths {
    const val RESET_HOUR = "day.resetHour"
    const val TOTAL_DAILY_MINUTES = "limits.totalDailyMinutes"
    const val STRETCH_MINUTES = "limits.stretch.minutes"
    const val NAG_REPEAT_MINUTES = "nags.repeatMinutes"
    const val SNOOZE_MINUTES = "nags.snoozeMinutes"
    const val BREAK_AFTER_MINUTES = "breaks.afterMinutes"
    const val FOCUS_DURATION_MINUTES = "focus.durationMinutes"
    const val FOCUS_CHECKIN_MINUTES = "focus.checkinMinutes"
    const val GOAL_FOCUS_SESSIONS = "goals.focusSessionsPerDay"
    const val GOAL_PRODUCTIVE_MINUTES = "goals.productiveMinutesPerDay"

    fun categoryDailyMinutes(category: Category) = "limits.categories.${category.id}.dailyMinutes"
}

/** Every tunable setting in the document by path, in the order S-06 and S-11 show them. */
fun NyxConfig.settingsByPath(): Map<String, Setting> = buildMap {
    put(SettingPaths.RESET_HOUR, day.resetHour)
    for (category in Category.entries) {
        limits.categories[category]?.let { put(SettingPaths.categoryDailyMinutes(category), it.dailyMinutes) }
    }
    put(SettingPaths.TOTAL_DAILY_MINUTES, limits.totalDailyMinutes)
    put(SettingPaths.STRETCH_MINUTES, limits.stretch.minutes)
    put(SettingPaths.NAG_REPEAT_MINUTES, nags.repeatMinutes)
    put(SettingPaths.SNOOZE_MINUTES, nags.snoozeMinutes)
    put(SettingPaths.BREAK_AFTER_MINUTES, breaks.afterMinutes)
    put(SettingPaths.FOCUS_DURATION_MINUTES, focus.durationMinutes)
    put(SettingPaths.FOCUS_CHECKIN_MINUTES, focus.checkinMinutes)
    put(SettingPaths.GOAL_FOCUS_SESSIONS, goals.focusSessionsPerDay)
    put(SettingPaths.GOAL_PRODUCTIVE_MINUTES, goals.productiveMinutesPerDay)
}

/**
 * The user's value wins only where the admin allows it, and only inside the admin's current range.
 * Clamping happens on every read, so the stored value survives a narrowed range (US-22).
 */
fun Setting.resolve(userValue: Int?): Int =
    if (!userEditable || userValue == null) default else userValue.coerceIn(min, max)

/** The base document with the user's values layered on top (§5). This is what features read. */
class EffectiveConfig(val document: NyxConfig, private val userValues: Map<String, Int>) {
    val settings: Map<String, Setting> = document.settingsByPath()

    /** `null` when the document has no such setting, e.g. a daily limit for a category without one. */
    fun valueOf(path: String): Int? = settings[path]?.resolve(userValues[path])
}
