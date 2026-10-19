package com.companionstudio.nyx.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Setup's own keys (§14.3). Progress is just [completed]: each step saves its own choices (D-56). */
class SetupSettings(private val store: DataStore<Preferences>) {
    val completed: Flow<Boolean> = store.data.map { it[COMPLETED] ?: false }
    val ageConfirmed: Flow<Boolean> = store.data.map { it[AGE_CONFIRMED] ?: false }

    suspend fun markCompleted() {
        store.edit { it[COMPLETED] = true }
    }

    // Only "yes" is stored; an under-18 answer isn't (D-57)
    suspend fun confirmAge() {
        store.edit { it[AGE_CONFIRMED] = true }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("setup.completed")
        val AGE_CONFIRMED = booleanPreferencesKey("setup.ageConfirmed")
    }
}
