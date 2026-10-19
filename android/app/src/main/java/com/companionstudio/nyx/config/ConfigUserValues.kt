package com.companionstudio.nyx.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The user's own values for user-editable settings, keyed `config.<setting path>` (§14.2).
 * Only values the user actually set are stored, so untouched settings follow the admin default (D-61).
 */
class ConfigUserValues(private val store: DataStore<Preferences>) {

    fun observe(): Flow<Map<String, Int>> = store.data.map { preferences ->
        preferences.asMap()
            .mapNotNull { (key, value) ->
                if (key.name.startsWith(PREFIX) && value is Int) key.name.removePrefix(PREFIX) to value else null
            }
            .toMap()
    }

    /** Clamped to the setting's range first, so nothing outside it is ever stored (D-61). */
    suspend fun set(path: String, setting: Setting, value: Int) {
        store.edit { it[intPreferencesKey(PREFIX + path)] = value.coerceIn(setting.min, setting.max) }
    }

    private companion object {
        const val PREFIX = "config."
    }
}
