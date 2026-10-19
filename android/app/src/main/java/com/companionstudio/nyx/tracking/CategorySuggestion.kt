package com.companionstudio.nyx.tracking

import android.content.pm.ApplicationInfo
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.NyxConfig

/**
 * The category an app gets until the user picks one (US-04): the admin default for the package,
 * otherwise Android's own app category mapped through the config, otherwise Neutral.
 * Suggestions are never stored, so a better admin default reaches every app the user didn't touch (D-58).
 */
fun suggestCategory(packageName: String, androidCategory: Int, config: NyxConfig): Category =
    config.categoryDefaults[packageName]
        ?: androidCategoryName(androidCategory)?.let { config.androidCategoryMap[it] }
        ?: Category.NEUTRAL

/** `ApplicationInfo.category` → the key used in the config's `androidCategoryMap`; `null` for anything else. */
fun androidCategoryName(androidCategory: Int): String? = when (androidCategory) {
    ApplicationInfo.CATEGORY_GAME -> "game"
    ApplicationInfo.CATEGORY_AUDIO -> "audio"
    ApplicationInfo.CATEGORY_VIDEO -> "video"
    ApplicationInfo.CATEGORY_IMAGE -> "image"
    ApplicationInfo.CATEGORY_SOCIAL -> "social"
    ApplicationInfo.CATEGORY_NEWS -> "news"
    ApplicationInfo.CATEGORY_MAPS -> "maps"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
    else -> null
}
