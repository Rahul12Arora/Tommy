package com.companionstudio.nyx.config

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class US21_ConfigManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val builtIn = SharedFiles.fixture("valid", "minimal") // configVersion 1
    private val rejected = mutableListOf<List<ConfigError>>()

    private fun cacheFile(): File = folder.root.resolve(ConfigManager.CACHE_FILE)

    private fun manager(builtInText: String = builtIn) =
        ConfigManager(readBuiltIn = { builtInText }, cacheFile = cacheFile(), onRejected = { rejected += it })

    private fun cache(text: String) = cacheFile().writeText(text)

    private fun rejectedRules() = rejected.map { errors -> errors.map { it.rule }.toSet() }

    @Test
    fun noCache_usesBuiltIn() {
        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(emptyList<Set<String>>(), rejectedRules())
    }

    @Test
    fun validCache_isUsed() {
        cache(builtIn.replace("\"configVersion\": 1", "\"configVersion\": 7"))

        assertEquals(7, manager().loadBase().configVersion)
        assertEquals(emptyList<Set<String>>(), rejectedRules())
    }

    @Test
    fun invalidCache_fallsBackToBuiltIn_andIsReported() {
        cache(SharedFiles.fixture("invalid", "setting-range--default-above-max"))

        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(listOf(setOf(ConfigRule.SETTING_RANGE)), rejectedRules())
    }

    @Test
    fun cacheThatIsNotJson_fallsBackToBuiltIn() {
        cache("{ half a document")

        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(listOf(setOf(ConfigRule.SCHEMA)), rejectedRules())
    }

    @Test
    fun cacheForAnotherSchemaVersion_fallsBackToBuiltIn() {
        cache(SharedFiles.fixture("invalid", "schema--wrong-schema-version"))

        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(listOf(setOf(ConfigRule.SCHEMA)), rejectedRules())
    }

    @Test
    fun unreadableCache_fallsBackToBuiltIn_andIsReported() {
        cache(builtIn)
        cacheFile().setReadable(false)
        // Root can read any file, so there's nothing to test there
        assumeFalse("running as root", cacheFile().canRead())

        assertEquals(1, manager().loadBase().configVersion)
        assertEquals(listOf(setOf(ConfigManager.CACHE_UNREADABLE)), rejectedRules())
    }

    @Test
    fun invalidBuiltIn_throws() {
        // A broken build, not a broken server: US21_DefaultConfigTest stops this from shipping
        assertThrows(IllegalStateException::class.java) {
            manager(builtInText = SharedFiles.fixture("invalid", "tick-order--fast-above-slow")).loadBase()
        }
    }

    // M12: documents fetched from the server (§14.12)

    private fun version(n: Int) = builtIn.replace("\"configVersion\": 1", "\"configVersion\": $n")

    @Test
    fun base_startsWithTheCache_orTheBuiltInWithoutOne() = runTest {
        assertEquals(1, manager().base.first().configVersion)

        cache(version(7))
        assertEquals(7, manager().base.first().configVersion)
    }

    @Test
    fun cachedVersion_isNullOnTheBuiltIn_andTheCachesVersionOtherwise() {
        assertNull(manager().cachedVersion())

        cache(version(7))
        assertEquals(7, manager().cachedVersion())
    }

    @Test
    fun cachedVersion_isNullWhenTheCacheWasRejected() {
        cache(SharedFiles.fixture("invalid", "setting-range--default-above-max"))

        assertNull(manager().cachedVersion())
    }

    @Test
    fun validNewDocument_isSaved_andBecomesTheBase() = runTest {
        val manager = manager()

        assertEquals(AcceptResult.Applied(2), manager.accept(version(2)))

        assertEquals(version(2), cacheFile().readText())
        assertEquals(2, manager.base.first().configVersion)
        assertEquals(2, manager.cachedVersion())
        // The next start reads it from the cache
        assertEquals(2, manager().loadBase().configVersion)
    }

    @Test
    fun everyInvalidFixture_isRejectedWithItsRule_andChangesNothing() = runTest {
        cache(version(7))
        val manager = manager()
        val fixtures = SharedFiles.fixtures("invalid")
        assertTrue(fixtures.isNotEmpty())

        for (file in fixtures) {
            val name = file.nameWithoutExtension
            val result = manager.accept(file.readText())

            assertTrue(name, result is AcceptResult.Rejected)
            val rules = (result as AcceptResult.Rejected).errors.map { it.rule }.toSet()
            assertTrue("$name: $rules", name.substringBefore("--") in rules)
        }
        assertEquals(version(7), cacheFile().readText())
        assertEquals(7, manager.base.first().configVersion)
        // Rejections of fetched documents are the refresher's to log, not the cache's
        assertEquals(emptyList<Set<String>>(), rejectedRules())
    }

    @Test
    fun anotherSchemaVersion_isRejected() {
        val result = manager().accept(SharedFiles.fixture("invalid", "schema--wrong-schema-version"))

        assertEquals(setOf(ConfigRule.SCHEMA), (result as AcceptResult.Rejected).errors.map { it.rule }.toSet())
        assertFalse(cacheFile().exists())
    }

    @Test
    fun sameVersion_isUnchanged_andNotRewritten() {
        cache(version(7))
        cacheFile().setLastModified(0)

        assertEquals(AcceptResult.Unchanged, manager().accept(version(7)))
        assertEquals(0, cacheFile().lastModified())
    }

    @Test
    fun lowerVersion_isApplied() = runTest {
        cache(version(7))
        val manager = manager()

        assertEquals(AcceptResult.Applied(3), manager.accept(version(3)))
        assertEquals(3, manager.base.first().configVersion)
    }

    @Test
    fun builtInVersion_isAppliedFromTheServer() {
        // The server's version 1 may differ from the built-in one, so it's taken (D-141)
        assertEquals(AcceptResult.Applied(1), manager().accept(version(1)))
        assertEquals(1, manager().cachedVersion())
    }

    @Test
    fun leftoverTempFile_doesNotAffectLoading_andIsReplaced() {
        cache(version(7))
        val temp = folder.root.resolve(ConfigManager.CACHE_FILE + ".tmp")
        temp.writeText("{ half a document")

        assertEquals(7, manager().loadBase().configVersion)
        assertEquals(AcceptResult.Applied(8), manager().accept(version(8)))
        assertFalse(temp.exists())
        assertEquals(version(8), cacheFile().readText())
    }

    @Test
    fun cacheThatCantBeWritten_isStillAppliedInMemory() = runTest {
        val readOnly = folder.newFolder("read-only")
        val manager = ConfigManager({ builtIn }, readOnly.resolve(ConfigManager.CACHE_FILE), onRejected = { rejected += it })
        readOnly.setWritable(false)
        // Root can write anywhere, so there's nothing to test there
        assumeFalse("running as root", readOnly.canWrite())

        val result = manager.accept(version(2)) as AcceptResult.Applied

        assertEquals(2, result.version)
        assertTrue(result.cacheError != null)
        assertEquals(2, manager.base.first().configVersion)
    }
}
