package com.companionstudio.nyx.tracking

import android.content.pm.ApplicationInfo
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.SharedFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class US04_CategorySuggestionTest {

    private val config = SharedFiles.minimalConfig.copy(
        categoryDefaults = mapOf("com.example.chat" to Category.SOCIAL),
        androidCategoryMap = mapOf("game" to Category.GAMES, "video" to Category.ENTERTAINMENT, "news" to Category.NEUTRAL),
    )

    @Test
    fun adminDefault_winsOverAndroidsCategory() {
        assertEquals(Category.SOCIAL, suggestCategory("com.example.chat", ApplicationInfo.CATEGORY_GAME, config))
    }

    @Test
    fun noAdminDefault_androidsCategoryIsMapped() {
        assertEquals(Category.GAMES, suggestCategory("com.example.puzzle", ApplicationInfo.CATEGORY_GAME, config))
        assertEquals(Category.ENTERTAINMENT, suggestCategory("com.example.tv", ApplicationInfo.CATEGORY_VIDEO, config))
    }

    @Test
    fun aMapEntryPointingAtNeutral_isUsedAsIs() {
        assertEquals(Category.NEUTRAL, suggestCategory("com.example.paper", ApplicationInfo.CATEGORY_NEWS, config))
    }

    @Test
    fun unmappedOrUndefinedCategory_isNeutral() {
        assertEquals(Category.NEUTRAL, suggestCategory("com.example.music", ApplicationInfo.CATEGORY_AUDIO, config))
        assertEquals(Category.NEUTRAL, suggestCategory("com.example.tool", ApplicationInfo.CATEGORY_UNDEFINED, config))
        assertEquals(Category.NEUTRAL, suggestCategory("com.example.a11y", ApplicationInfo.CATEGORY_ACCESSIBILITY, config))
    }

    @Test
    fun androidCategoryNames_matchTheConfigKeys() {
        assertEquals(
            listOf("game", "audio", "video", "image", "social", "news", "maps", "productivity"),
            listOf(
                ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO,
                ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS,
                ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ).map(::androidCategoryName),
        )
        assertNull(androidCategoryName(42))
    }
}
