package com.companionstudio.nyx.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
/**
 * One colour per category for S-10's chart (§14.13), in the stack order. The dataviz reference palette's first five
 * slots, which pass its checks as neighbours in both modes; in light mode three sit below 3:1 on the surface, so the
 * day list under the chart carries every value as text.
 */
val CategoryColorsLight = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4))
val CategoryColorsDark = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181))
