package com.companionstudio.nyx.focus

import com.companionstudio.nyx.MainDispatcherRule
import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.data.AdjustableClock
import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.tracking.InstalledApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** S-09's choices and Start (US-12, D-117). */
@OptIn(ExperimentalCoroutinesApi::class)
class US12_FocusSetupViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
    private val config = EffectiveConfig(defaults, emptyMap())
    private val dao = FakeFocusSessionDao()
    private val clock = AdjustableClock()
    private val repository = FocusRepository(dao, flowOf(config), DayClock(clock), clock)
    private val apps = listOf(
        InstalledApp(INSTAGRAM, "Instagram", 0),
        InstalledApp(YOUTUBE, "YouTube", 0),
        InstalledApp("com.android.chrome", "Chrome", 0),
    )

    private fun viewModel(fromApp: String? = null) = main.track(FocusSetupViewModel({ apps }, flowOf(config), repository, fromApp))

    @Test
    fun D117_withNoEarlierSession_theConfigDefaults_andNoApp() {
        val choices = initialChoices(last = null, config, fromApp = null)

        assertEquals(FocusChoices(FocusGoal.FIXED_TIME, 25, emptySet(), PetMode.CHECK_IN, 10), choices)
    }

    @Test
    fun D117_theLastSessionsChoices_clampedIntoTodaysRanges() {
        val last = session(goal = FocusGoal.NO_SWITCHING, goalMinutes = 500, apps = listOf(YOUTUBE), petMode = PetMode.QUIET, checkinMinutes = 1)

        val choices = initialChoices(last, config, fromApp = null)

        assertEquals(FocusChoices(FocusGoal.NO_SWITCHING, 180, setOf(YOUTUBE), PetMode.QUIET, 5), choices)
    }

    @Test
    fun D117_anUntilDoneLastSession_leavesTheMinutesAtTheConfigValue() {
        val last = session(goal = FocusGoal.UNTIL_DONE, goalMinutes = null, petMode = PetMode.REWARD_AT_END, checkinMinutes = null)
        val userValue = EffectiveConfig(defaults, mapOf(SettingPaths.FOCUS_DURATION_MINUTES to 40))

        val choices = initialChoices(last, userValue, fromApp = null)

        assertEquals(40, choices.minutes)
        assertEquals(10, choices.checkinMinutes)
    }

    @Test
    fun D117_fromTheRing_theAppInFrontIsTheSelection() {
        val last = session(apps = listOf(INSTAGRAM, "com.android.chrome"))

        assertEquals(setOf(YOUTUBE), initialChoices(last, config, fromApp = YOUTUBE).apps)
    }

    @Test
    fun US05_lockedSliders_showTheAdminValue_notTheLastSessions() {
        val locked = defaults.copy(focus = defaults.focus.copy(durationMinutes = defaults.focus.durationMinutes.copy(userEditable = false)))

        assertEquals(25, initialChoices(session(goalMinutes = 60), EffectiveConfig(locked, emptyMap()), fromApp = null).minutes)
    }

    @Test
    fun appChoices_pickedFirst_andSearchMatchesAnywhereIgnoringCase() {
        assertEquals(listOf(YOUTUBE, INSTAGRAM, "com.android.chrome"), appChoices(apps, setOf(YOUTUBE), query = "").map { it.packageName })
        assertEquals(listOf(YOUTUBE), appChoices(apps, emptySet(), query = " tube").map { it.packageName })
    }

    @Test
    fun US12_startIsOff_untilAnInstalledAppIsPicked() = runTest {
        // The last session's only app is gone
        dao.start(session(apps = listOf("com.uninstalled")).copy(outcome = FocusOutcome.COMPLETED))
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertFalse(viewModel.state.first { it?.apps != null }!!.canStart)
        viewModel.toggleApp(YOUTUBE)
        assertTrue(viewModel.state.first { it!!.canStart }!!.choices.apps.contains(YOUTUBE))
    }

    @Test
    fun US12_start_writesOneSession_withOnlyInstalledApps_andASecondStartWritesNothing() = runTest {
        val viewModel = viewModel(fromApp = YOUTUBE)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.state.first { it?.canStart == true }
        viewModel.setGoal(FocusGoal.UNTIL_DONE)
        viewModel.setPetMode(PetMode.QUIET)
        var started = 0

        viewModel.start { started++ }.join()
        viewModel.start { started++ }.join()

        assertEquals(1, started)
        val written = dao.sessions.value.single()
        assertEquals(listOf(YOUTUBE), written.allowedApps)
        assertEquals(FocusGoal.UNTIL_DONE, written.goal)
        assertNull("no minutes for until done", written.goalMinutes)
        assertNull("no check-ins in quiet mode", written.checkinMinutes)
    }
}
