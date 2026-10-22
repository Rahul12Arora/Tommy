package com.companionstudio.nyx.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface AcceptResult {
    /** [cacheError] is set when the document couldn't be saved: it's used until the app restarts. */
    data class Applied(val version: Int, val cacheError: String? = null) : AcceptResult
    data object Unchanged : AcceptResult
    data class Rejected(val errors: List<ConfigError>) : AcceptResult
}

/**
 * Picks the base document (§5, US-21): the last good admin config if the cache holds a valid one,
 * otherwise the built-in defaults. A document fetched later replaces it while the app runs (§14.12).
 */
class ConfigManager(
    private val readBuiltIn: () -> String,
    private val cacheFile: File,
    /** Told why a cached document was ignored, so it can be logged. */
    private val onRejected: (List<ConfigError>) -> Unit,
) {
    private class Start(val config: NyxConfig, val fromServer: Boolean)

    // Read from disk once, the first time anything needs it
    private val start by lazy { readStart() }

    // A document from the server accepted since then (D-144)
    private val fetched = MutableStateFlow<NyxConfig?>(null)

    /** The base document: what [loadBase] found, then every document [accept] applies. */
    val base: Flow<NyxConfig> = fetched.map { it ?: start.config }

    /** The document found at start: the cache, or the built-in one. */
    fun loadBase(): NyxConfig = start.config

    /** The version of the server's document in use, which is its ETag; `null` on the built-in one (D-141). */
    fun cachedVersion(): Int? = fetched.value?.configVersion ?: start.takeIf { it.fromServer }?.config?.configVersion

    /**
     * Checks a fetched document like the cache and, if it's valid and a different version (lower ones too,
     * D-144), saves it and makes it the base. A rejected document changes nothing.
     */
    fun accept(text: String): AcceptResult {
        val config = when (val result = parseConfig(text)) {
            is ConfigResult.Valid -> result.config
            is ConfigResult.Invalid -> return AcceptResult.Rejected(result.errors)
        }
        if (config.configVersion == cachedVersion()) return AcceptResult.Unchanged
        val cacheError = try {
            writeCache(text)
            null
        } catch (e: IOException) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
        fetched.value = config
        return AcceptResult.Applied(config.configVersion, cacheError)
    }

    private fun readStart(): Start {
        val cached = readCache()
        if (cached != null) {
            when (val result = parseConfig(cached)) {
                is ConfigResult.Valid -> return Start(result.config, fromServer = true)
                is ConfigResult.Invalid -> onRejected(result.errors)
            }
        }
        return when (val result = parseConfig(readBuiltIn())) {
            is ConfigResult.Valid -> Start(result.config, fromServer = false)
            // US21_DefaultConfigTest fails the build first, so this means a broken build, not a broken server
            is ConfigResult.Invalid -> error("Built-in config is invalid: ${result.errors}")
        }
    }

    private fun readCache(): String? {
        if (!cacheFile.isFile) return null
        return try {
            cacheFile.readText()
        } catch (e: IOException) {
            onRejected(listOf(ConfigError(CACHE_UNREADABLE, cacheFile.name, e.message ?: e.javaClass.simpleName)))
            null
        }
    }

    // Written next to the cache, then renamed over it in one step: a crash mid-write leaves the old cache whole
    private fun writeCache(text: String) {
        val temp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        FileOutputStream(temp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        Files.move(temp.toPath(), cacheFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val BUILT_IN_ASSET = "default-config.json"
        const val CACHE_FILE = "config_cache.json"

        /** Not a validation rule: the cache file exists but couldn't be read. */
        const val CACHE_UNREADABLE = "cache-unreadable"
    }
}
