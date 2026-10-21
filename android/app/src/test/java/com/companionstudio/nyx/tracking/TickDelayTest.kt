package com.companionstudio.nyx.tracking

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Default config: slow 30 s, fast 5 s; Social 60 min a day; total off; stretch 30 min on Social and Games. */
class TickDelayTest {

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config

    private fun config(userValues: Map<String, Int> = emptyMap()) = EffectiveConfig(defaults, userValues)

    private fun snapshot(category: Category?, todayMs: Map<Category, Long> = emptyMap(), stretchMs: Long = 0) =
        TrackerSnapshot(
            day = LocalDate.parse("2026-10-21"),
            frontPackage = category?.let { "com.example.app" },
            frontCategory = category,
            stretchMs = stretchMs,
            todayMs = todayMs,
        )

    private fun minutes(minutes: Int, seconds: Int = 0) = (minutes * 60L + seconds) * 1000

    @Test
    fun nothingCountedInFront_isSlow() {
        assertEquals(30, tickDelaySeconds(snapshot(category = null), config()))
    }

    @Test
    fun farFromEveryLimit_isSlow() {
        val snapshot = snapshot(Category.SOCIAL, mapOf(Category.SOCIAL to minutes(10)), stretchMs = minutes(10))
        assertEquals(30, tickDelaySeconds(snapshot, config()))
    }

    @Test
    fun categoryLimitWithinOneSlowTick_isFast() {
        val snapshot = snapshot(Category.SOCIAL, mapOf(Category.SOCIAL to minutes(59, 40)), stretchMs = minutes(5))
        assertEquals(5, tickDelaySeconds(snapshot, config()))
    }

    @Test
    fun stretchWithinOneSlowTick_isFast_onlyForStretchCategories() {
        val social = snapshot(Category.SOCIAL, mapOf(Category.SOCIAL to minutes(29, 50)), stretchMs = minutes(29, 50))
        val neutral = snapshot(Category.NEUTRAL, mapOf(Category.NEUTRAL to minutes(1)), stretchMs = minutes(29, 50))

        assertEquals(5, tickDelaySeconds(social, config()))
        assertEquals(30, tickDelaySeconds(neutral, config()))
    }

    @Test
    fun totalLimitWithinOneSlowTick_isFast_forAnyCountedApp() {
        val usage = mapOf(Category.PRODUCTIVE to minutes(100), Category.NEUTRAL to minutes(19, 45))
        val snapshot = snapshot(Category.PRODUCTIVE, usage)

        assertEquals(30, tickDelaySeconds(snapshot, config())) // total is off by default
        assertEquals(5, tickDelaySeconds(snapshot, config(mapOf(SettingPaths.TOTAL_DAILY_MINUTES to 120))))
    }

    @Test
    fun aLimitAlreadyPassed_isSlow_unlessThePetGlows() {
        val snapshot = snapshot(Category.SOCIAL, mapOf(Category.SOCIAL to minutes(61)), stretchMs = minutes(1))
        assertEquals(30, tickDelaySeconds(snapshot, config()))
        assertEquals(5, tickDelaySeconds(snapshot, config(), glowing = true))
    }

    @Test
    fun D107_continuousUseWithinOneSlowTickOfTheBreakLimit_isFast_evenWithNothingCountedInFront() {
        val near = snapshot(category = null).copy(continuousMs = minutes(44, 40))
        val far = snapshot(category = null).copy(continuousMs = minutes(40))
        val breaksOff = EffectiveConfig(defaults.copy(features = defaults.features.copy(breaks = false)), emptyMap())

        assertEquals(5, tickDelaySeconds(near, config()))
        assertEquals(30, tickDelaySeconds(far, config()))
        assertEquals(30, tickDelaySeconds(near, breaksOff))
    }

    @Test
    fun aLimitThatsOff_neverMakesItFast() {
        val socialOff = config(mapOf(SettingPaths.categoryDailyMinutes(Category.SOCIAL) to 0))
        val snapshot = snapshot(Category.SOCIAL, mapOf(Category.SOCIAL to 0), stretchMs = 0)

        assertEquals(30, tickDelaySeconds(snapshot, socialOff))
    }
}
