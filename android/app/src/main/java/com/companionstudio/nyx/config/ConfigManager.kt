package com.companionstudio.nyx.config

import java.io.File
import java.io.IOException

/**
 * Picks the base document (§5, US-21): the last good admin config if the cache holds a valid one,
 * otherwise the built-in defaults. Nothing writes the cache until M12.
 */
class ConfigManager(
    private val readBuiltIn: () -> String,
    private val cacheFile: File,
    /** Told why a cached document was ignored, so it can be logged. */
    private val onRejected: (List<ConfigError>) -> Unit,
) {
    fun loadBase(): NyxConfig {
        val cached = readCache()
        if (cached != null) {
            when (val result = parseConfig(cached)) {
                is ConfigResult.Valid -> return result.config
                is ConfigResult.Invalid -> onRejected(result.errors)
            }
        }
        return when (val result = parseConfig(readBuiltIn())) {
            is ConfigResult.Valid -> result.config
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

    companion object {
        const val BUILT_IN_ASSET = "default-config.json"
        const val CACHE_FILE = "config_cache.json"

        /** Not a validation rule: the cache file exists but couldn't be read. */
        const val CACHE_UNREADABLE = "cache-unreadable"
    }
}
