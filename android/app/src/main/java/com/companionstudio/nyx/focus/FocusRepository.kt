package com.companionstudio.nyx.focus

import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SettingPaths
import com.companionstudio.nyx.data.DayClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock

/** What S-09 asks for (US-12). [goalMinutes] is ignored for until done, [checkinMinutes] outside check-in mode. */
data class FocusRequest(
    val goal: FocusGoal,
    val goalMinutes: Int,
    val allowedApps: List<String>,
    val petMode: PetMode,
    val checkinMinutes: Int,
)

/**
 * Focus sessions for the app's screens. They start and end sessions by writing storage, and the service follows
 * (D-44, D-109); the overlay's buttons go to the engine instead.
 */
class FocusRepository(
    private val dao: FocusSessionDao,
    private val effectiveConfig: Flow<EffectiveConfig>,
    private val dayClock: DayClock,
    private val clock: Clock,
) {
    fun observeActive(): Flow<FocusSession?> = dao.observeActive()

    /** The running session as Home and S-09 show it; `null` when none runs. */
    fun observeStatus(): Flow<FocusStatus?> =
        combine(dao.observeActive(), effectiveConfig) { session, config -> session?.let { focusStatus(it, config.document.focus) } }

    /** `features.focus`: off hides Start, but a running session carries on (D-120). */
    fun observeEnabled(): Flow<Boolean> = effectiveConfig.map { it.document.features.focus }

    suspend fun latest(): FocusSession? = dao.latest()

    /** `false` when a session is already running (one at a time) or no app was chosen. */
    suspend fun start(request: FocusRequest): Boolean {
        if (request.allowedApps.isEmpty()) return false
        val now = clock.instant()
        val session = FocusSession(
            day = today().toString(),
            startedAtMs = now.toEpochMilli(),
            goal = request.goal,
            goalMinutes = request.goalMinutes.takeIf { request.goal != FocusGoal.UNTIL_DONE },
            allowedApps = request.allowedApps.distinct(),
            petMode = request.petMode,
            checkinMinutes = request.checkinMinutes.takeIf { request.petMode == PetMode.CHECK_IN },
        )
        return dao.start(session) != null
    }

    /** Done ([done]) or End on Home or S-09. Done that isn't allowed yet (D-113) ends nothing. */
    suspend fun finish(done: Boolean): Boolean {
        val active = dao.active() ?: return false
        val minDone = effectiveConfig.first().document.focus.minDoneMinutes
        if (done && !canFinishAsDone(active, minDone)) return false
        val outcome = if (done) FocusOutcome.COMPLETED else FocusOutcome.ENDED_EARLY
        // FOCUS_COMPLETED goes on today, the day mood is shown for (D-116)
        return dao.finish(active.id, outcome, clock.instant().toEpochMilli(), today().toString())
    }

    private suspend fun today() =
        dayClock.dayKeyAt(clock.instant(), checkNotNull(effectiveConfig.first().valueOf(SettingPaths.RESET_HOUR)))
}
