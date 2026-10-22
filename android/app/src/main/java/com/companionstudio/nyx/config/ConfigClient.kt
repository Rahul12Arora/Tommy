package com.companionstudio.nyx.config

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface FetchResult {
    data class Fetched(val body: String) : FetchResult
    data object NotModified : FetchResult
    data class Failed(val reason: String) : FetchResult
}

/**
 * One `GET /v1/config` (§14.12). Knows HTTP, not config: the body is checked by [ConfigManager.accept].
 * Never throws for a network or server problem; the refresher decides when to try again.
 */
class ConfigClient(
    private val baseUrl: String,
    private val appVersion: String,
    private val timeoutMs: Int = 10_000,
) {
    /** Blocking; call on Dispatchers.IO. */
    fun fetch(cachedVersion: Int?): FetchResult {
        val connection = try {
            URL("$baseUrl/v1/config?schema=$SCHEMA_VERSION").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return failed(e)
        }
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            // The ETag is ours to handle; a cached response would hide what the server says
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            // The default names the phone model, and nothing about the phone leaves it (§9, D-145)
            connection.setRequestProperty("User-Agent", "Nyx/$appVersion")
            // Only a document from the server has a version the server knows (D-141)
            if (cachedVersion != null) connection.setRequestProperty("If-None-Match", "\"$cachedVersion\"")

            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.use { readCapped(it) }?.let { FetchResult.Fetched(it) }
                        ?: FetchResult.Failed("too large")
                HttpURLConnection.HTTP_NOT_MODIFIED -> FetchResult.NotModified
                else -> FetchResult.Failed("HTTP $status")
            }
        } catch (e: IOException) {
            failed(e)
        } finally {
            connection.disconnect()
        }
    }

    /** The body as UTF-8, or `null` past [MAX_BYTES], so a broken server can't exhaust memory (D-145). */
    private fun readCapped(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) return out.toString(Charsets.UTF_8.name())
            out.write(buffer, 0, read)
            if (out.size() > MAX_BYTES) return null
        }
    }

    private fun failed(e: IOException) = FetchResult.Failed("${e.javaClass.simpleName}: ${e.message}")

    companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
