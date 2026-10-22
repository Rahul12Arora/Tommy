package com.companionstudio.nyx.setup

import com.companionstudio.nyx.MainDispatcherRule
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.ConfigUserValues
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.Setting
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.data.createSettingsStore
import com.companionstudio.nyx.ui.sliderStepMinutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class US05_LimitsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    // Social 60 (0–240), Games 120 (0–480), Entertainment 120 (0–480), total 0 = off (0–960), stretch 30 (10–120)
    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
    private val social = SettingPaths.categoryDailyMinutes(Category.SOCIAL)



    @Test
    fun limitsThenPositiveGoals_inDisplayOrder_withTheirEffectiveValues() {
        val rows = limitRows(EffectiveConfig(defaults, mapOf(social to 45)))

        assertEquals(
            listOf(
                LimitName.CategoryDaily(Category.SOCIAL),
                LimitName.CategoryDaily(Category.GAMES),
                LimitName.CategoryDaily(Category.ENTERTAINMENT),
                LimitName.TotalDaily,
                LimitName.Stretch(listOf(Category.SOCIAL, Category.GAMES)),
                LimitName.FocusSessions,
                LimitName.ProductiveMinutes,
            ),
            rows.map { it.name },
        )
        assertEquals(listOf(45, 120, 120, 0, 30, 0, 0), rows.map { it.value })
        assertEquals(listOf(false, false, false, false, false, true, true), rows.map { it.isGoal })
    }

    @Test
    fun categoryWithoutALimit_hasNoRow() {
        val rows = limitRows(EffectiveConfig(SharedFiles.minimalConfig, emptyMap())) // only Social has a limit

        assertEquals(
            listOf(social, SettingPaths.TOTAL_DAILY_MINUTES, SettingPaths.STRETCH_MINUTES, SettingPaths.GOAL_FOCUS_SESSIONS, SettingPaths.GOAL_PRODUCTIVE_MINUTES),
            rows.map { it.path },
        )
    }

    @Test
    fun lockedSetting_isReadOnly_andShowsTheAdminDefault() {
        val locked = defaults.copy(
            limits = defaults.limits.copy(totalDailyMinutes = Setting(default = 300, min = 0, max = 960, userEditable = false)),
        )

        val total = limitRows(EffectiveConfig(locked, mapOf(SettingPaths.TOTAL_DAILY_MINUTES to 100)))
            .single { it.name == LimitName.TotalDaily }

        assertFalse(total.editable)
        assertEquals(300, total.value)
    }

    @Test
    fun set_isStoredClampedToTheRange_andTheRowsFollow() = runTest {
        val userValues = ConfigUserValues(createSettingsStore({ folder.root.resolve("settings.preferences_pb") }, onCorrupted = {}))
        val config = userValues.observe().map { EffectiveConfig(defaults, it) }
        val viewModel = main.track(LimitsViewModel(config, userValues))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.rows.collect {} }
        val socialRow = viewModel.rows.first { it != null }!!.first { it.path == social }

        viewModel.set(socialRow, 999).join()

        assertEquals(240, userValues.observe().first()[social])
        assertEquals(240, viewModel.rows.first { rows -> rows?.first { it.path == social }?.value == 240 }!!.first().value)
    }

    @Test
    fun sliderSteps_fiveOnLongEvenRanges_otherwiseOne() {
        assertEquals(5, sliderStepMinutes(Setting(60, 0, 240, true)))
        assertEquals(5, sliderStepMinutes(Setting(30, 10, 120, true)))
        assertEquals(1, sliderStepMinutes(Setting(10, 5, 30, true))) // short range
        assertEquals(1, sliderStepMinutes(Setting(60, 0, 241, true))) // doesn't divide by 5
        assertEquals(1, sliderStepMinutes(Setting(60, 0, 60, true))) // exactly 60 isn't "longer than 60"
    }
}
