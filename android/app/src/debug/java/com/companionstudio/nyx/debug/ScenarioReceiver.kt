package com.companionstudio.nyx.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.companionstudio.nyx.PetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

/**
 * Debug builds only (D-67). Plays a scenario file from `assets/scenarios/`, optionally with time running faster:
 *
 * `adb shell am broadcast -n com.companionstudio.nyx/.debug.ScenarioReceiver --es scenario instagram-65min --ei speed 60`
 *
 * `--ez stop true` goes back to real usage and the real clock. Both clear all tracked time first, so every run
 * starts from the same place. The pet service must be running (finish setup, or open the app).
 */
class ScenarioReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PetApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.database.dailyUsageDao().clearAll()
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
            } catch (e: Exception) {
                Log.e(TAG, "Scenario not started", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Nyx"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_STOP = "stop"
    }
}
