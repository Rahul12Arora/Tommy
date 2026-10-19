package com.companionstudio.nyx.config

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One problem with a document. [rule] is one of [ConfigRule]; invalid fixtures are named after it (D-53). */
data class ConfigError(val rule: String, val path: String, val message: String)

sealed interface ConfigResult {
    data class Valid(val config: NyxConfig) : ConfigResult
    data class Invalid(val errors: List<ConfigError>) : ConfigResult
}

// Unknown keys are allowed so additive changes don't need a new schema version (D-52)
private val configJson = Json { ignoreUnknownKeys = true }

/** Parses and validates a config document. Never throws for a bad document. */
fun parseConfig(text: String): ConfigResult {
    val config = try {
        configJson.decodeFromString<NyxConfig>(text)
    } catch (e: SerializationException) {
        // Not JSON, a missing field, a wrong type or an unknown category: all things JSON Schema catches
        return ConfigResult.Invalid(listOf(ConfigError(ConfigRule.SCHEMA, "$", e.message ?: e.javaClass.simpleName)))
    }
    val errors = validateConfig(config)
    return if (errors.isEmpty()) ConfigResult.Valid(config) else ConfigResult.Invalid(errors)
}
