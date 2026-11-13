package com.tomasps.virga.core.network

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.testing.TEST_TOKEN
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.max

private const val ZONE_OK = """{"success":true,"errors":[],"messages":[],"result":{}}"""
private const val ACCOUNT_PATH = "/accounts/a/r2/buckets/b/objects/"

class HardeningTest {

    private fun timeoutSeenBy(sink: (HttpTimeoutConfig?) -> Unit, body: String, status: HttpStatusCode = HttpStatusCode.OK): CloudflareApi =
        CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            liveEngine = MockEngine { request ->
                sink(request.getCapabilityOrNull(HttpTimeoutCapability))
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )

    @Test
    fun `a dot segment is refused before any request is sent`() = runBlocking {
        var calls = 0
        val api = CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            liveEngine = MockEngine { calls++; respond("", HttpStatusCode.OK) },
        )
        for (key in listOf("..", ".", "%2E%2E", "%2e")) {
            val result = api.delete<Unit>(ACCOUNT_PATH + key)
            assertTrue("key $key", (result as ApiResult.Failure).error is ApiError.Local)
        }
        assertEquals(0, calls)
    }

    @Test
    fun `an ordinary key that merely contains dots is allowed`() = runBlocking {
        val api = mockCloudflareApi { delete("/accounts/a/r2/buckets/b/objects/{key...}") { DemoReply.empty() } }
        assertTrue(api.delete<Unit>(cloudflarePath("accounts", "a", "r2", "buckets", "b", "objects", "a..b.txt")) is ApiResult.Success)
    }

    @Test
    fun `download is not cut off by the total request timeout`() = runBlocking {
        var timeout: HttpTimeoutConfig? = null
        val api = timeoutSeenBy({ timeout = it }, body = "data")
        api.download("/accounts/a/r2/buckets/b/objects/k") { _, _ -> Unit }
        assertEquals(HttpTimeoutConfig.INFINITE_TIMEOUT_MS, timeout?.requestTimeoutMillis)
        assertEquals(CloudflareEndpoints.STREAM_IDLE_TIMEOUT_MILLIS, timeout?.socketTimeoutMillis)
    }

    @Test
    fun `a plain call keeps the bounded request timeout`() = runBlocking {
        var timeout: HttpTimeoutConfig? = null
        val api = timeoutSeenBy({ timeout = it }, body = ZONE_OK)
        api.get<Unit>("/zones/z1")
        assertEquals(CloudflareEndpoints.REQUEST_TIMEOUT_MILLIS, timeout?.requestTimeoutMillis)
    }

    @Test
    fun `an exception thrown by the download consumer propagates unchanged`() {
        val api = mockCloudflareApi {
            get("/accounts/a/r2/buckets/b/objects/{key...}") { DemoReply.raw(ByteArray(3)) }
        }
        try {
            runBlocking { api.download("/accounts/a/r2/buckets/b/objects/k") { _, _ -> throw IllegalStateException("disk full") } }
            fail("consumer exception was swallowed")
        } catch (error: IllegalStateException) {
            assertEquals("disk full", error.message)
        }
    }

    @Test
    fun `an empty successful reply is Unit for a Unit call`() = runBlocking {
        val api = timeoutSeenBy({}, body = "", status = HttpStatusCode.NoContent)
        assertTrue(api.delete<Unit>("/zones/z1") is ApiResult.Success)
    }

    @Test
    fun `an empty successful reply is still a decode error for a typed call`() = runBlocking {
        val api = timeoutSeenBy({}, body = "")
        assertTrue(api.get<Zone>("/zones/z1") is ApiResult.Failure)
    }

    @kotlinx.serialization.Serializable
    data class Zone(val id: String)

    @Test
    fun `a page-style walk follows the server's own page size when it caps per_page`() = runBlocking {
        val pagesRequested = mutableListOf<Int>()
        val api = mockCloudflareApi {
            get("/zones") { request ->
                val page = request.query["page"]!!.toInt()
                pagesRequested += page
                val from = (page - 1) * SERVER_CAP + 1
                DemoReply.ok(items(from, SERVER_CAP), buildJsonObject {
                    put("page", page); put("per_page", SERVER_CAP); put("count", SERVER_CAP); put("total_count", TOTAL)
                })
            }
        }
        val ids = api.allPages("/zones", deserializer = itemsSerializer, perPage = 50)
        assertEquals(listOf(1, 2, 3, 4, 5), pagesRequested)
        assertEquals((1..TOTAL).toList(), (ids as ApiResult.Success).value.map { it.id })
    }

    @Test
    fun `a list longer than the page bound ends with a failure instead of a silent success`() = runBlocking {
        val api = mockCloudflareApi {
            get("/zones") { request ->
                val page = request.query["page"]!!.toInt()
                DemoReply.ok(items((page - 1) * 50 + 1, 50), buildJsonObject {
                    put("page", page); put("per_page", 50); put("count", 50); put("total_count", 100_000)
                })
            }
        }
        val result = api.allPages("/zones", deserializer = itemsSerializer)
        assertTrue(result is ApiResult.Failure && result.error is ApiError.Local)
    }

    @Test
    fun `a page walk stops exactly at the last page without a failure`() = runBlocking {
        val api = mockCloudflareApi {
            get("/zones") { request ->
                DemoReply.ok(items(1, 2), buildJsonObject { put("page", 1); put("per_page", 2); put("count", 2); put("total_count", 2) })
            }
        }
        val pages = api.pagedFlow("/zones", deserializer = itemsSerializer, perPage = 2).toList()
        assertEquals(1, pages.size)
    }

    @Test
    fun `a short rate limit on a GET is retried once after waiting`() = runBlocking {
        var clock = 0L
        val limiter = RateLimiter(nowNanos = { clock * NANOS_PER_MILLI }, delayMillis = { clock += it })
        var calls = 0
        val api = CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            rateLimiter = limiter,
            liveEngine = MockEngine { _ ->
                calls++
                if (calls == 1) {
                    respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "2"))
                } else {
                    respond(ZONE_OK, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        )
        val result = api.get<Unit>("/zones/z1")
        assertTrue(result is ApiResult.Success)
        assertEquals(2, calls)
        assertTrue(clock >= 2_000L)
    }

    @Test
    fun `a long rate limit on a GET is returned to the caller without retry`() = runBlocking {
        var calls = 0
        val api = CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            liveEngine = MockEngine { _ ->
                calls++
                respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "600"))
            },
        )
        val error = (api.get<Unit>("/zones/z1") as ApiResult.Failure).error
        assertEquals(600_000L, (error as ApiError.RateLimited).retryAfterMillis)
        assertEquals(1, calls)
    }

    @Test
    fun `a demo route registered twice with the same shape is rejected`() {
        val registry = DemoRouteRegistry().get("/accounts/{id}") { DemoReply.empty() }
        try {
            registry.get("/accounts/{accountId}") { DemoReply.empty() }
            fail("duplicate shape accepted")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("duplicates"))
        }
    }

    @Test
    fun `a binary demo upload reaches the handler byte for byte`() = runBlocking {
        val payload = byteArrayOf(0, -1, 127, -128, 10, 13)
        var received: ByteArray? = null
        val api = mockCloudflareApi {
            put("/accounts/a/r2/buckets/b/objects/{key...}") { request ->
                received = request.bodyBytes
                DemoReply.empty()
            }
        }
        val result = api.uploadStream(
            method = HttpMethod.Put,
            path = "/accounts/a/r2/buckets/b/objects/blob",
            contentType = ContentType.Application.OctetStream,
            contentLength = payload.size.toLong(),
            write = { channel -> channel.writeFully(payload) },
            deserializer = Unit.serializer(),
        )
        assertTrue(result is ApiResult.Success)
        assertTrue(payload.contentEquals(received))
    }

    private val itemsSerializer = ListSerializer(Item.serializer())

    @kotlinx.serialization.Serializable
    data class Item(val id: Int)

    private fun items(from: Int, count: Int): JsonArray = buildJsonArray {
        for (i in from until from + count) add(buildJsonObject { put("id", i) })
    }

    private companion object {
        const val SERVER_CAP = 20
        const val TOTAL = 100
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
