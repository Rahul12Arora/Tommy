package com.companionstudio.nyx.config

import com.companionstudio.nyx.rules.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class US19_ConfigRefresherTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val builtIn = SharedFiles.fixture("valid", "minimal") // configVersion 1
    private val clock = TestClock(Instant.parse("2026-10-22T08:00:00Z"))
    private val screenOn = MutableStateFlow(true)
    private val manager by lazy { ConfigManager({ builtIn }, folder.root.resolve(ConfigManager.CACHE_FILE), onRejected = {}) }
    private val logs = mutableListOf<String>()

    /** The cached version sent with each check. */
    private val checks = mutableListOf<Int?>()
    private var answer: FetchResult = FetchResult.NotModified

    private val refresher by lazy {
        ConfigRefresher(fetch = { checks += it; answer }, manager = manager, clock = clock, log = { logs += it })
    }

    private fun version(n: Int) = builtIn.replace("\"configVersion\": 1", "\"configVersion\": $n")

    /** Starts the refresher; virtual time moves with [pass], the wall clock with it. */
    private fun TestScope.start() {
        backgroundScope.launch { refresher.keepFresh(screenOn) }
        runCurrent()
    }

    private fun TestScope.pass(duration: Duration) {
        clock.now += duration
        advanceTimeBy(duration.toMillis())
        runCurrent()
    }

    @Test
    fun checksAtOnceOnStart_withTheScreenOn() = runTest {
        start()

        assertEquals(listOf<Int?>(null), checks)
    }

    @Test
    fun checksAgainOnlyAfterSixHours() = runTest {
        start()

        pass(Duration.ofHours(5).plusMinutes(50))
        assertEquals(1, checks.size)

        pass(Duration.ofMinutes(15))
        assertEquals(2, checks.size)
    }

    @Test
    fun clockMovedBack_checks() = runTest {
        start()

        clock.now -= Duration.ofHours(1)
        pass(ConfigRefresher.LOOK_EVERY)

        assertEquals(2, checks.size)
    }

    @Test
    fun screenOff_noChecks_thenChecksWhenItComesBackOn() = runTest {
        screenOn.value = false
        start()
        pass(Duration.ofHours(7))
        assertEquals(0, checks.size)

        screenOn.value = true
        runCurrent()
        assertEquals(1, checks.size)

        screenOn.value = false
        pass(Duration.ofHours(7))
        assertEquals(1, checks.size)

        screenOn.value = true
        runCurrent()
        assertEquals(2, checks.size)
    }

    @Test
    fun screenBackOnWithinSixHours_doesNotCheck() = runTest {
        start()
        screenOn.value = false
        pass(Duration.ofHours(1))

        screenOn.value = true
        runCurrent()

        assertEquals(1, checks.size)
    }

    @Test
    fun failedCheck_waitsForTheNextSixHours() = runTest {
        answer = FetchResult.Failed("HTTP 503")
        start()

        pass(Duration.ofHours(5).plusMinutes(50))
        assertEquals(1, checks.size)
        assertEquals(listOf("Config check failed: HTTP 503"), logs)
    }

    @Test
    fun fetchedDocument_isApplied_andItsVersionSentNextTime() = runTest {
        answer = FetchResult.Fetched(version(2))
        start()

        assertEquals(2, manager.base.first().configVersion)
        assertEquals(listOf("Config 2 applied"), logs)

        answer = FetchResult.NotModified
        pass(ConfigRefresher.CHECK_EVERY)
        assertEquals(listOf(null, 2), checks)
        assertEquals("Config 2 unchanged (304)", logs.last())
    }

    @Test
    fun invalidDocument_isLogged_andTheBaseStays() = runTest {
        answer = FetchResult.Fetched(SharedFiles.fixture("invalid", "setting-range--default-above-max"))
        start()

        assertEquals(1, manager.base.first().configVersion)
        assertEquals(1, logs.size)
        assertEquals(true, logs.single().startsWith("Fetched config ignored: setting-range at "))
    }

    @Test
    fun notModified_appliesNothing() = runTest {
        folder.root.resolve(ConfigManager.CACHE_FILE).writeText(version(7))
        start()

        assertEquals(listOf<Int?>(7), checks)
        assertEquals(7, manager.base.first().configVersion)
        assertEquals(listOf("Config 7 unchanged (304)"), logs)
    }
}
