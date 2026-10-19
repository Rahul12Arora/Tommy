package com.companionstudio.nyx.setup

import com.companionstudio.nyx.data.createSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SetupSettingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val corrupted = mutableListOf<Exception>()

    private fun settingsFile() = folder.root.resolve("settings.preferences_pb")

    private fun settings() = SetupSettings(createSettingsStore(::settingsFile, onCorrupted = { corrupted += it }))

    @Test
    fun freshInstall_nothingDone() = runTest {
        val settings = settings()

        assertFalse(settings.completed.first())
        assertFalse(settings.ageConfirmed.first())
    }

    @Test
    fun markCompleted_isStored() = runTest {
        val settings = settings()

        settings.markCompleted()

        assertTrue(settings.completed.first())
        assertFalse(settings.ageConfirmed.first())
    }

    @Test
    fun confirmAge_isStored() = runTest {
        val settings = settings()

        settings.confirmAge()

        assertTrue(settings.ageConfirmed.first())
        assertFalse(settings.completed.first())
    }

    @Test
    fun corruptFile_readsAsDefaults_andIsReported() = runTest {
        settingsFile().writeText("not a preferences file")

        val settings = settings()

        assertFalse(settings.completed.first())
        assertEquals(1, corrupted.size)
    }
}
