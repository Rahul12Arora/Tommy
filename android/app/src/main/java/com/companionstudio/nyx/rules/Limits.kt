package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.BubbleTrigger
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.Placeholder
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.id
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.tracking.TrackerSnapshot

/** The keys nags, snoozes and `LIMIT_EXCEEDED` events are about (§14.7). */
object LimitKeys {
    const val TOTAL = "limit:total"
    const val STRETCH = "stretch"

    fun category(category: Category) = "limit:${category.id}"
}

/** Names for a line's placeholders, from the app's resources and the package manager. */
interface NagLabels {
    fun app(packageName: String): String

    /** The category's display name; `null` is the total limit ("screen time"). */
    fun category(category: Category?): String
}

/**
 * One limit that's on, as it stands this tick. [category] is `null` for the total and the stretch.
 * [covered]: the app in front is one this limit is about.
 */
data class LimitCheck(
    val key: String,
    val category: Category?,
    val usedMs: Long,
    val limitMinutes: Int,
    val covered: Boolean,
) {
    val over: Boolean get() = usedMs >= limitMinutes * 60_000L
    val isStretch: Boolean get() = key == LimitKeys.STRETCH

    val firstTrigger: BubbleTrigger get() = if (isStretch) BubbleTrigger.STRETCH_REACHED else BubbleTrigger.LIMIT_REACHED

    // A stretch has no category or daily limit to name, so it repeats with its own lines (§14.7)
    val repeatTrigger: BubbleTrigger get() = if (isStretch) BubbleTrigger.STRETCH_REACHED else BubbleTrigger.LIMIT_REPEAT
}

/** Every limit that's on (not 0): each category's daily limit, the total, the stretch, with effective values. */
fun limitChecks(snapshot: TrackerSnapshot, config: EffectiveConfig): List<LimitCheck> = buildList {
    val front = snapshot.frontCategory
    for (category in Category.entries) {
        val limit = config.valueOf(SettingPaths.categoryDailyMinutes(category)) ?: continue
        if (limit > 0) add(LimitCheck(LimitKeys.category(category), category, snapshot.todayMs[category] ?: 0, limit, front == category))
    }
    val total = checkNotNull(config.valueOf(SettingPaths.TOTAL_DAILY_MINUTES))
    if (total > 0) add(LimitCheck(LimitKeys.TOTAL, null, snapshot.todayMs.values.sum(), total, covered = front != null))
    val stretch = checkNotNull(config.valueOf(SettingPaths.STRETCH_MINUTES))
    val stretchCovers = front != null && front in config.document.limits.stretch.categories
    if (stretch > 0) add(LimitCheck(LimitKeys.STRETCH, null, snapshot.stretchMs, stretch, stretchCovers))
}

/** The nag bubble for [check], with every placeholder its trigger allows filled in (§14.6). */
fun nagRequest(check: LimitCheck, trigger: BubbleTrigger, frontPackage: String?, labels: NagLabels): BubbleRequest {
    val app = frontPackage?.let(labels::app)
    val values = if (check.isStretch) {
        mapOf(Placeholder.MINUTES to check.limitMinutes.toString())
    } else {
        mapOf(Placeholder.CATEGORY to labels.category(check.category), Placeholder.LIMIT to formatDuration(check.limitMinutes * 60L))
    }
    return BubbleRequest(trigger, if (app == null) values else values + (Placeholder.APP to app), key = check.key)
}
