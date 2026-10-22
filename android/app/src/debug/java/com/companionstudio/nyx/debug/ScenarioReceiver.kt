package com.companionstudio.nyx.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.companionstudio.nyx.AppContainer
import com.companionstudio.nyx.PetApp
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.focus.FocusGoal
import com.companionstudio.nyx.focus.FocusRequest
import com.companionstudio.nyx.focus.PetMode
import com.companionstudio.nyx.rules.DailyGoal
import com.companionstudio.nyx.rules.PetEvent
import com.companionstudio.nyx.rules.PetEventType
import com.companionstudio.nyx.rules.goalsOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

/**
 * Debug builds only (D-67). Plays a scenario file from `assets/scenarios/`, optionally with time running faster:
 *
 * `adb shell am broadcast -n com.companionstudio.nyx/.debug.ScenarioReceiver --es scenario instagram-65min --ei speed 60`
 *
 * `--ez stop true` goes back to real usage and the real clock. Both clear all tracked time and the event log first,
 * so every run starts from the same place. The pet service must be running (finish setup, or open the app).
 *
 * `--es event break_taken` logs one event of that [PetEventType] now and clears nothing. It stands in for the
 * positive events later milestones write, so a pet that ran away can be brought back (D-86).
 *
 * With a scenario, `--es focus no_switching --ei focus_minutes 25 --es focus_apps com.google.android.youtube
 * --es focus_mode check_in [--ei focus_checkin 10]` also starts a focus session through the same path as S-09 (D-119).
 * Goals: `fixed_time`, `until_done`, `no_switching`; modes: `quiet`, `reward_at_end`, `check_in`. Starting a scenario
 * or stopping one also clears the focus sessions.
 *
 * `--es seed trends-30d` clears the same, then writes [SEEDED_DAYS] past days for Trends (D-155) and logs one line per
 * day to compare with S-10. Real usage and the real clock carry on for today. Every path that clears also records
 * today's goals again, as the engine does on a new day, since a running engine only writes them when they change.
 */
class ScenarioReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PetApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                intent.getStringExtra(EXTRA_EVENT)?.let { name ->
                    logEvent(container, PetEventType.valueOf(name.uppercase()))
                    return@launch
                }
                container.database.dailyUsageDao().clearAll()
                container.database.petEventDao().clearAll()
                container.focusSessionDao.clearAll()
                container.database.dailyGoalDao().clearAll()
                recordTodaysGoals(container)
                intent.getStringExtra(EXTRA_SEED)?.let { name ->
                    require(name == TRENDS_SEED) { "Unknown seed $name; the one seed is $TRENDS_SEED" }
                    seedTrends(container)
                    return@launch
                }
                if (intent.getBooleanExtra(EXTRA_STOP, false)) {
                    container.clock.reset()
                    container.usageSource.value = container.systemUsageSource
                    Log.i(TAG, "Scenario stopped: real usage and real time again")
                    return@launch
                }
                val name = requireNotNull(intent.getStringExtra(EXTRA_SCENARIO)) { "Pass --es $EXTRA_SCENARIO <name>" }
                val steps = parseScenario(context.assets.open("scenarios/$name.txt").bufferedReader().use { it.readText() })
                container.clock.runFaster(intent.getIntExtra(EXTRA_SPEED, 1))
                container.usageSource.value = ScenarioUsageSource(scenarioEvents(steps, start = container.clock.instant().truncatedTo(ChronoUnit.MILLIS)))
                Log.i(TAG, "Scenario $name started at ${container.clock.speed}× speed")
                intent.getStringExtra(EXTRA_FOCUS)?.let { goal ->
                    // The engine starts a new run for the new usage source; a session from before that run is picked
                    // up silently, so give it a moment, or there's no focus_started line
                    delay(500)
                    startFocus(container, intent, FocusGoal.valueOf(goal.uppercase()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scenario not started", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun startFocus(container: AppContainer, intent: Intent, goal: FocusGoal) {
        val request = FocusRequest(
            goal = goal,
            goalMinutes = intent.getIntExtra(EXTRA_FOCUS_MINUTES, 25),
            allowedApps = requireNotNull(intent.getStringExtra(EXTRA_FOCUS_APPS)) { "Pass --es $EXTRA_FOCUS_APPS <packages>" }.split(","),
            petMode = PetMode.valueOf((intent.getStringExtra(EXTRA_FOCUS_MODE) ?: "check_in").uppercase()),
            checkinMinutes = intent.getIntExtra(EXTRA_FOCUS_CHECKIN, 10),
        )
        val started = container.focusRepository.start(request)
        Log.i(TAG, if (started) "Focus session started: $request" else "Focus session not started")
    }

    private suspend fun recordTodaysGoals(container: AppContainer) {
        val config = container.effectiveConfig.first()
        val today = container.dayClock.today(checkNotNull(config.valueOf(SettingPaths.RESET_HOUR))).toString()
        container.database.dailyGoalDao().replaceDay(today, goalsOn(config).map { DailyGoal(today, it.key, it.target) })
    }

    private suspend fun seedTrends(container: AppContainer) {
        val resetHour = checkNotNull(container.effectiveConfig.first().valueOf(SettingPaths.RESET_HOUR))
        val seed = trendSeed(container.dayClock.today(resetHour))
        val database = container.database
        // The cursor moves to now: today was just cleared, so there's nothing earlier to credit
        database.dailyUsageDao().record(seed.usage, creditedUntilMs = container.clock.instant().toEpochMilli())
        database.dailyGoalDao().insertAll(seed.goals)
        database.petEventDao().insertAll(seed.events)
        Log.i(TAG, "Seeded $TRENDS_SEED: ${seed.lines.size} days")
        seed.lines.forEach { Log.i(TAG, "Seed $it") }
    }

    private suspend fun logEvent(container: AppContainer, type: PetEventType) {
        val resetHour = checkNotNull(container.effectiveConfig.first().valueOf(SettingPaths.RESET_HOUR))
        val now = container.clock.instant()
        val day = container.dayClock.dayKeyAt(now, resetHour).toString()
        container.database.petEventDao().insertAll(listOf(PetEvent(day = day, atMs = now.toEpochMilli(), type = type)))
        Log.i(TAG, "Logged $type for $day")
    }

    private companion object {
        const val TAG = "Nyx"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_STOP = "stop"
        const val EXTRA_EVENT = "event"
        const val EXTRA_SEED = "seed"
        const val TRENDS_SEED = "trends-30d"
        const val EXTRA_FOCUS = "focus"
        const val EXTRA_FOCUS_MINUTES = "focus_minutes"
        const val EXTRA_FOCUS_APPS = "focus_apps"
        const val EXTRA_FOCUS_MODE = "focus_mode"
        const val EXTRA_FOCUS_CHECKIN = "focus_checkin"
    }
}
