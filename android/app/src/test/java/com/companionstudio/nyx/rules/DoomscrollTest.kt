package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class DoomscrollTest {

    private val social = listOf(Category.SOCIAL)

    private fun minutes(minutes: Long, seconds: Long = 0) = minutes * 60_000 + seconds * 1000

    @Test
    fun oneEventPerFullTenMinutes() {
        assertEquals(0, doomscrollEventsDue(mapOf(Category.SOCIAL to minutes(9, 59)), social, logged = 0))
        assertEquals(1, doomscrollEventsDue(mapOf(Category.SOCIAL to minutes(10)), social, logged = 0))
        assertEquals(2, doomscrollEventsDue(mapOf(Category.SOCIAL to minutes(25)), social, logged = 0))
    }

    @Test
    fun onlyTheDoomscrollCategoriesCount() {
        val today = mapOf(Category.SOCIAL to minutes(6), Category.GAMES to minutes(6), Category.ENTERTAINMENT to minutes(30))

        assertEquals(0, doomscrollEventsDue(today, social, logged = 0))
        assertEquals(1, doomscrollEventsDue(today, listOf(Category.SOCIAL, Category.GAMES), logged = 0))
    }

    @Test
    fun alreadyLogged_onlyTheMissingOnes() {
        assertEquals(0, doomscrollEventsDue(mapOf(Category.SOCIAL to minutes(25)), social, logged = 2))
        assertEquals(1, doomscrollEventsDue(mapOf(Category.SOCIAL to minutes(30)), social, logged = 2))
    }

    @Test
    fun fewerDueThanLogged_isZero() {
        // The admin removed a category from doomscrolling mid-day
        assertEquals(0, doomscrollEventsDue(mapOf(Category.GAMES to minutes(30)), social, logged = 3))
    }
}
