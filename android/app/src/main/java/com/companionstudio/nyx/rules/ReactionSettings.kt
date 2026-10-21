package com.companionstudio.nyx.rules

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The user's stored choices. [mode] is what they picked; whether the admin allows it is checked on use (D-98). */
data class ReactionPrefs(val mode: BreakMode = BreakMode.GENTLE, val idleEnabled: Boolean = true)

/** How the pet reacts, as the user set it in S-11 (§14.7). The service observes it (D-44). */
class ReactionSettings(private val store: DataStore<Preferences>) {
    val prefs: Flow<ReactionPrefs> = store.data.map { prefs ->
        ReactionPrefs(
            mode = BreakMode.entries.firstOrNull { it.name.lowercase() == prefs[MODE] } ?: BreakMode.GENTLE,
            idleEnabled = prefs[IDLE_ENABLED] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun setMode(mode: BreakMode) {
        store.edit { it[MODE] = mode.name.lowercase() }
    }

    suspend fun setIdleEnabled(enabled: Boolean) {
        store.edit { it[IDLE_ENABLED] = enabled }
    }

    private companion object {
        val MODE = stringPreferencesKey("breaks.mode")
        val IDLE_ENABLED = booleanPreferencesKey("idle.enabled")
    }
}

/** Obstructive only when the user picked it and the admin allows it; the stored choice is kept either way. */
fun effectiveBreakMode(prefs: ReactionPrefs, allowed: Boolean): BreakMode =
    if (prefs.mode == BreakMode.OBSTRUCTIVE && allowed) BreakMode.OBSTRUCTIVE else BreakMode.GENTLE
