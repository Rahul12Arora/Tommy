package com.companionstudio.nyx.config

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class US21_ConfigClientTest {

    private lateinit var server: HttpServer
    private var respond: (HttpExchange) -> Unit = { it.send(200, "{}") }
    private var lastRequest: HttpExchange? = null

    private val client get() = ConfigClient("http://127.0.0.1:${server.address.port}", "1.2", timeoutMs = 500)

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastRequest = exchange
            exchange.use(respond)
        }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

    private fun HttpExchange.send(status: Int, body: String? = null) {
        val bytes = body?.toByteArray(Charsets.UTF_8)
        // -1: no body (a 304 must have none)
        sendResponseHeaders(status, bytes?.size?.toLong() ?: -1)
        bytes?.let { responseBody.write(it) }
    }

    @Test
    fun asksForThisBuildsSchema_withOurOwnUserAgent() {
        client.fetch(null)

        val request = checkNotNull(lastRequest)
        assertEquals("/v1/config?schema=$SCHEMA_VERSION", request.requestURI.toString())
        assertEquals("Nyx/1.2", request.requestHeaders.getFirst("User-Agent"))
        assertEquals("application/json", request.requestHeaders.getFirst("Accept"))
    }

    @Test
    fun cachedVersion_isSentAsETag() {
        client.fetch(7)

        assertEquals("\"7\"", lastRequest?.requestHeaders?.getFirst("If-None-Match"))
    }

    @Test
    fun noCachedVersion_noETag() {
        client.fetch(null)

        assertNull(lastRequest?.requestHeaders?.getFirst("If-None-Match"))
    }

    @Test
    fun ok_returnsTheBody() {
        respond = { it.send(200, """{"face":"😸"}""") }

        assertEquals(FetchResult.Fetched("""{"face":"😸"}"""), client.fetch(null))
    }

    @Test
    fun notModified() {
        respond = { it.send(304) }

        assertEquals(FetchResult.NotModified, client.fetch(7))
    }

    @Test
    fun otherStatuses_fail() {
        respond = { it.send(404, """{"error":{"code":"NOT_FOUND"}}""") }
        assertEquals(FetchResult.Failed("HTTP 404"), client.fetch(null))

        respond = { it.send(500, "{}") }
        assertEquals(FetchResult.Failed("HTTP 500"), client.fetch(null))
    }

    @Test
    fun serverNotRunning_fails() {
        val offline = client
        server.stop(0)

        assertTrue(offline.fetch(null) is FetchResult.Failed)
    }

    @Test
    fun noAnswerWithinTheTimeout_fails() {
        val release = CountDownLatch(1)
        respond = { release.await(5, TimeUnit.SECONDS) }

        val result = client.fetch(null)
        release.countDown()

        assertTrue(result.toString(), result is FetchResult.Failed && "Timeout" in result.reason)
    }

    @Test
    fun bodyOverTheCap_fails() {
        respond = { it.send(200, "x".repeat(ConfigClient.MAX_BYTES + 1)) }
        assertEquals(FetchResult.Failed("too large"), client.fetch(null))

        respond = { it.send(200, "x".repeat(ConfigClient.MAX_BYTES)) }
        assertTrue(client.fetch(null) is FetchResult.Fetched)
    }
}
