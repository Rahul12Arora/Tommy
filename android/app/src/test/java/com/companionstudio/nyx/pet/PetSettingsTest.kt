package com.companionstudio.nyx.pet

import com.companionstudio.nyx.data.createSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class PetSettingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun settings() = PetSettings(
        createSettingsStore({ folder.root.resolve("settings.preferences_pb") }, onCorrupted = { throw it }),
    )

    @Test
    fun D73_position_defaultsToTheRightEdge_30PercentDown() = runTest {
        assertEquals(PetPosition(Side.RIGHT, 0.3f), settings().position.first())
    }

    @Test
    fun D73_position_isStored() = runTest {
        val settings = settings()

        settings.savePosition(PetPosition(Side.LEFT, 0.75f))

        assertEquals(PetPosition(Side.LEFT, 0.75f), settings.position.first())
    }

    @Test
    fun D73_aStoredFractionOutside0To1_isClampedOnRead() = runTest {
        val settings = settings()

        settings.savePosition(PetPosition(Side.LEFT, 1.4f))

        assertEquals(PetPosition(Side.LEFT, 1f), settings.position.first())
    }

    @Test
    fun US29_hiddenUntil_isStoredAndCleared() = runTest {
        val settings = settings()
        assertNull(settings.hiddenUntil.first())

        settings.setHiddenUntil(Instant.parse("2026-10-21T10:30:00Z"))
        assertEquals(Instant.parse("2026-10-21T10:30:00Z"), settings.hiddenUntil.first())

        settings.setHiddenUntil(null)
        assertNull(settings.hiddenUntil.first())
    }
}
