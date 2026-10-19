package com.companionstudio.nyx.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Opt-in for anonymous daily stats (US-24): off unless the user turns it on. The install ID and uploads come in M14. */
class StatsSettings(private val store: DataStore<Preferences>) {
    val optIn: Flow<Boolean> = store.data.map { it[OPT_IN] ?: false }

    suspend fun setOptIn(on: Boolean) {
        store.edit { it[OPT_IN] = on }
    }

    private companion object {
        val OPT_IN = booleanPreferencesKey("stats.optIn")
    }
}
