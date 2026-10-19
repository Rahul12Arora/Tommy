package com.companionstudio.nyx

import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Puts the app's settings into a known state before the Activity starts: a fresh install, or setup already done.
 * Goes through the app's own DataStore, never the file underneath it. Use it with a lower `order` than the compose rule.
 */
class SetupStateRule(private val completed: Boolean) : ExternalResource() {
    override fun before() = runBlocking {
        val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PetApp).container
        container.settingsStore.edit { it.clear() }
        if (completed) container.setupSettings.markCompleted()
    }
}
