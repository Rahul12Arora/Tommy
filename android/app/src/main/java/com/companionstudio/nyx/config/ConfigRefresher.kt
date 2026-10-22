package com.companionstudio.nyx.config

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * When to check the server for a new config (A-17, D-143): at service start, then every 6 hours of wall-clock
 * time, and only while the screen is on, so nothing wakes the phone. A failed check waits for the next one (§9).
 */
class ConfigRefresher(
    private val fetch: (cachedVersion: Int?) -> FetchResult,
    private val manager: ConfigManager,
    private val clock: Clock,
    private val log: (String) -> Unit,
) {
    // In memory, so every service start checks; a 304 costs a few hundred bytes
    private var lastCheck: Instant? = null

    /** Runs until the caller's scope ends. Blocks on the network, so call it on Dispatchers.IO. */
    suspend fun keepFresh(screenOn: Flow<Boolean>) {
        // Screen off cancels the wait; screen on looks again at once
        screenOn.collectLatest { on ->
            while (on) {
                if (isDue()) check()
                delay(LOOK_EVERY.toMillis())
            }
        }
    }

    // Outside [lastCheck, lastCheck + 6 h) counts as due, so a clock moved back can't stop checks
    private fun isDue(): Boolean {
        val last = lastCheck ?: return true
        val now = clock.instant()
        return now.isBefore(last) || !now.isBefore(last + CHECK_EVERY)
    }

    private fun check() {
        lastCheck = clock.instant()
        val cachedVersion = manager.cachedVersion()
        when (val result = fetch(cachedVersion)) {
            FetchResult.NotModified -> log("Config $cachedVersion unchanged (304)")
            is FetchResult.Failed -> log("Config check failed: ${result.reason}")
            is FetchResult.Fetched -> when (val accepted = manager.accept(result.body)) {
                is AcceptResult.Applied ->
                    log("Config ${accepted.version} applied" + accepted.cacheError?.let { "; not saved: $it" }.orEmpty())
                AcceptResult.Unchanged -> log("Config $cachedVersion unchanged")
                // Rule IDs and paths are enough to find the problem in the published document
                is AcceptResult.Rejected ->
                    log("Fetched config ignored: " + accepted.errors.joinToString { "${it.rule} at ${it.path}" })
            }
        }
    }

    companion object {
        val CHECK_EVERY: Duration = Duration.ofHours(6)
        val LOOK_EVERY: Duration = Duration.ofMinutes(15)
    }
}
