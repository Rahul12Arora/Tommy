package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.BubbleTrigger
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.Placeholder
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.tracking.TrackerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Default config: Social 60 min, Games and Entertainment 120 min a day, total off, stretch 30 min on Social and Games. */
class US08_LimitsTest {

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config

    private fun config(userValues: Map<String, Int> = emptyMap()) = EffectiveConfig(defaults, userValues)

    private fun snapshot(front: Category?, todayMinutes: Map<Category, Long> = emptyMap(), stretchMinutes: Long = 0) = TrackerSnapshot(
        day = LocalDate.parse("2026-10-21"),
        frontPackage = front?.let { "com.example.${it.name.lowercase()}" },
        frontCategory = front,
        stretchMs = stretchMinutes * 60_000,
        todayMs = todayMinutes.mapValues { it.value * 60_000 },
    )

    private fun checks(snapshot: TrackerSnapshot, userValues: Map<String, Int> = emptyMap()) =
        limitChecks(snapshot, config(userValues)).associateBy { it.key }

    @Test
    fun oneCheckPerLimitThatsOn_andAZeroLimitIsOff() {
        assertEquals(
            listOf("limit:social", "limit:games", "limit:entertainment", LimitKeys.STRETCH),
            limitChecks(snapshot(front = null), config()).map { it.key },
        )

        val socialOff = checks(snapshot(front = null), mapOf(SettingPaths.categoryDailyMinutes(Category.SOCIAL) to 0))
        assertFalse("limit:social" in socialOff)

        val totalOn = checks(snapshot(front = null), mapOf(SettingPaths.TOTAL_DAILY_MINUTES to 180))
        assertEquals(180, totalOn.getValue(LimitKeys.TOTAL).limitMinutes)
    }

    @Test
    fun US08_aCategoryLimit_coversOnlyItsCategory_andIsOverAtTheLimit() {
        val checks = checks(snapshot(Category.SOCIAL, mapOf(Category.SOCIAL to 60, Category.GAMES to 10)))

        assertTrue(checks.getValue("limit:social").covered)
        assertTrue(checks.getValue("limit:social").over)
        assertFalse(checks.getValue("limit:games").covered)
        assertFalse(checks.getValue("limit:games").over)
    }

    @Test
    fun US08_theTotal_coversEveryCountedApp_andAddsUpEveryCategory() {
        val totalOn = mapOf(SettingPaths.TOTAL_DAILY_MINUTES to 90)
        val total = checks(snapshot(Category.PRODUCTIVE, mapOf(Category.SOCIAL to 50, Category.PRODUCTIVE to 40)), totalOn)
            .getValue(LimitKeys.TOTAL)

        assertTrue(total.covered)
        assertTrue(total.over)
        assertFalse(checks(snapshot(front = null), totalOn).getValue(LimitKeys.TOTAL).covered)
    }

    @Test
    fun US09_theStretch_coversItsCategories_andUsesTheStretchTime() {
        val stretch = checks(snapshot(Category.GAMES, stretchMinutes = 30)).getValue(LimitKeys.STRETCH)

        assertTrue(stretch.covered)
        assertTrue(stretch.over)
        assertFalse(checks(snapshot(Category.ENTERTAINMENT, stretchMinutes = 30)).getValue(LimitKeys.STRETCH).covered)
    }

    @Test
    fun D83_nagRequests_fillEveryPlaceholderTheirTriggerAllows() {
        val snapshot = snapshot(Category.SOCIAL, mapOf(Category.SOCIAL to 60), stretchMinutes = 30)
        val all = checks(snapshot, mapOf(SettingPaths.TOTAL_DAILY_MINUTES to 60))

        for (check in all.values) {
            for (trigger in listOf(check.firstTrigger, check.repeatTrigger)) {
                val request = nagRequest(check, trigger, snapshot.frontPackage, TestLabels)
                assertEquals("${check.key} ${trigger.id}", trigger.placeholders, request.values.keys)
                assertEquals(check.key, request.key)
            }
        }
        val social = nagRequest(all.getValue("limit:social"), BubbleTrigger.LIMIT_REACHED, snapshot.frontPackage, TestLabels)
        assertEquals(mapOf(Placeholder.CATEGORY to "SOCIAL", Placeholder.LIMIT to "1 h", Placeholder.APP to "App(com.example.social)"), social.values)
        val total = nagRequest(all.getValue(LimitKeys.TOTAL), BubbleTrigger.LIMIT_REPEAT, snapshot.frontPackage, TestLabels)
        assertEquals("screen time", total.values[Placeholder.CATEGORY])
        val stretch = nagRequest(all.getValue(LimitKeys.STRETCH), BubbleTrigger.STRETCH_REACHED, snapshot.frontPackage, TestLabels)
        assertEquals("30", stretch.values[Placeholder.MINUTES])
    }

    @Test
    fun theStretch_repeatsWithItsOwnLines() {
        val stretch = checks(snapshot(Category.SOCIAL, stretchMinutes = 30)).getValue(LimitKeys.STRETCH)
        val social = checks(snapshot(Category.SOCIAL)).getValue("limit:social")

        assertEquals(BubbleTrigger.STRETCH_REACHED, stretch.firstTrigger)
        assertEquals(BubbleTrigger.STRETCH_REACHED, stretch.repeatTrigger)
        assertEquals(BubbleTrigger.LIMIT_REACHED, social.firstTrigger)
        assertEquals(BubbleTrigger.LIMIT_REPEAT, social.repeatTrigger)
    }
}
