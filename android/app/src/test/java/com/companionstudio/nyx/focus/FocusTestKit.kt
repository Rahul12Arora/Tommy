package com.companionstudio.nyx.focus

import com.companionstudio.nyx.rules.FakePetEventDao
import com.companionstudio.nyx.rules.PetEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Focus sessions in memory. `finish` and `start` run the real DAO code on top; events land in [events]. */
class FakeFocusSessionDao(private val events: FakePetEventDao = FakePetEventDao()) : FocusSessionDao() {
    val sessions = MutableStateFlow<List<FocusSession>>(emptyList())
    val progressSaves = mutableListOf<Long>()

    override fun observeActive(): Flow<FocusSession?> = sessions.map { all -> all.firstOrNull { it.outcome == null } }

    override suspend fun active() = sessions.value.firstOrNull { it.outcome == null }

    override suspend fun byId(id: Long) = sessions.value.firstOrNull { it.id == id }

    override suspend fun latest() = sessions.value.lastOrNull()

    override suspend fun saveProgress(id: Long, focusMs: Long, switches: Int, lastFocusAtMs: Long) {
        progressSaves += id
        update(id) { if (it.outcome == null) it.copy(focusMs = focusMs, switches = switches, lastFocusAtMs = lastFocusAtMs) else it }
    }

    override suspend fun deleteBefore(day: String) {
        sessions.value = sessions.value.filterNot { it.day < day && it.outcome != null }
    }

    override suspend fun clearAll() {
        sessions.value = emptyList()
    }

    override suspend fun insert(session: FocusSession): Long {
        val id = sessions.value.size + 1L
        sessions.value = sessions.value + session.copy(id = id)
        return id
    }

    override suspend fun markEnded(id: Long, outcome: String, endedAtMs: Long): Int {
        val target = byId(id)?.takeIf { it.outcome == null } ?: return 0
        update(target.id) { it.copy(outcome = FocusOutcome.valueOf(outcome), endedAtMs = endedAtMs) }
        return 1
    }

    override suspend fun insertEvent(event: PetEvent) = events.insertAll(listOf(event))

    private fun update(id: Long, change: (FocusSession) -> FocusSession) {
        sessions.value = sessions.value.map { if (it.id == id) change(it) else it }
    }
}

fun session(
    goal: FocusGoal = FocusGoal.FIXED_TIME,
    goalMinutes: Int? = 25,
    apps: List<String> = listOf(YOUTUBE),
    petMode: PetMode = PetMode.CHECK_IN,
    checkinMinutes: Int? = 10,
    focusMs: Long = 0,
    switches: Int = 0,
    startedAtMs: Long = 0,
    day: String = "2026-10-21",
) = FocusSession(
    day = day,
    startedAtMs = startedAtMs,
    goal = goal,
    goalMinutes = goalMinutes,
    allowedApps = apps,
    petMode = petMode,
    checkinMinutes = checkinMinutes,
    focusMs = focusMs,
    switches = switches,
)

const val YOUTUBE = "com.google.android.youtube"
const val INSTAGRAM = "com.instagram.android"
const val DIALER = "com.google.android.dialer"
