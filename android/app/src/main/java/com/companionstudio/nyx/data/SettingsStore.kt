package com.companionstudio.nyx.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File

const val SETTINGS_STORE_NAME = "settings"

/**
 * The one Preferences DataStore (§14.1). Create it once, in AppContainer: a second instance on the same file throws.
 * A corrupt file is replaced with an empty one, so every setting falls back to its default instead of crashing.
 */
fun createSettingsStore(produceFile: () -> File, onCorrupted: (Exception) -> Unit): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { e ->
            onCorrupted(e)
            emptyPreferences()
        },
        produceFile = produceFile,
    )
