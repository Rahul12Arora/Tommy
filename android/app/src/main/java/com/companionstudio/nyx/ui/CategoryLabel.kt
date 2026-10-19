package com.companionstudio.nyx.ui

import androidx.annotation.StringRes
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Category

/** Display names live in strings.xml, not in config (D-51). */
val Category.labelRes: Int
    @StringRes get() = when (this) {
        Category.SOCIAL -> R.string.category_social
        Category.GAMES -> R.string.category_games
        Category.ENTERTAINMENT -> R.string.category_entertainment
        Category.PRODUCTIVE -> R.string.category_productive
        Category.NEUTRAL -> R.string.category_neutral
    }
