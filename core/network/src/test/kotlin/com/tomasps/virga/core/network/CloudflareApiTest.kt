package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.PageInfo
import com.tomasps.virga.core.network.testing.TEST_TOKEN
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpMethod
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareApiTest {
    @Serializable
    data class Zone(val id: String, val name: String)

    private val envelopeOk = """{"success":true,"errors":[],"messages":[],"result":{"id":"z1","name":"example.com"}}"""

    @Test
    fun `get decodes the result and sends the bearer token and a non-Cloudflare user agent`() = runBlocking {
        var seen: HttpRequestData? = null
        val api = CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            liveEngine = MockEngine { request ->
                seen = request
                respond(envelopeOk, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val result = api.get<Zone>("/zones/z1")
        assertEquals(Zone("z1", "example.com"), (result as ApiResult.Success).value)
        val request = seen!!
        assertEquals("https://api.cloudflare.com/client/v4/zones/z1", request.url.toString())
        assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.Authorization])
        assertFalse(request.headers[HttpHeaders.UserAgent].orEmpty().contains("cloudflare", ignoreCase = true))
    }

    @Test
    fun `missing credential fails before any request is sent`() = runBlocking {
        var calls = 0
        val api = CloudflareApi.create(
            credentials = CredentialProvider { null },
            liveEngine = MockEngine { calls++; respond("", HttpStatusCode.OK) },
        )
        val error = (api.get<Zone>("/zones/z1") as ApiResult.Failure).error
        assertTrue(error is ApiError.Unauthorized)
        assertEquals(0, calls)
    }

    @Test
    fun `post serialises the body as JSON`() = runBlocking {
        var body = ""
        val api = mockCloudflareApi {
            post("/zones/z1/settings") { request ->
                body = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("ok", true) })
            }
        }
        @Serializable data class Setting(val mode: String)
        @Serializable data class Ack(val ok: Boolean)
        val result = api.post<Setting, Ack>("/zones/z1/settings", Setting("on"))
        assertEquals(true, (result as ApiResult.Success).value.ok)
        assertEquals("""{"mode":"on"}""", body)
    }

    @Test
    fun `envelope errors map to typed errors with Cloudflare codes`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/a/workers/scripts") {
                DemoReply.failure(HttpStatusCode.Forbidden, code = 10000, message = "Authentication error")
            }
        }
        val error = (api.get<List<Zone>>("/accounts/a/workers/scripts") as ApiResult.Failure).error
        assertTrue(error is ApiError.Forbidden)
        assertEquals(listOf(10000), error.codes)
    }

    @Test
    fun `a 429 returns RateLimited with the Retry-After delay`() = runBlocking {
        val api = CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            liveEngine = MockEngine {
                respond(
                    """{"success":false,"errors":[{"code":10100,"message":"limit"}]}""",
                    HttpStatusCode.TooManyRequests,
                    headersOf(HttpHeaders.RetryAfter, "42"),
                )
            },
        )
        val error = (api.get<Zone>("/zones/z1") as ApiResult.Failure).error as ApiError.RateLimited
        assertEquals(42_000L, error.retryAfterMillis)
    }

    @Test
    fun `a transport failure is a Network error and does not throw`() = runBlocking {
        val api = CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            liveEngine = MockEngine { throw java.io.IOException("unreachable") },
        )
        assertTrue((api.get<Zone>("/zones/z1") as ApiResult.Failure).error is ApiError.Network)
    }

    @Test
    fun `a 500 is a Server error`() = runBlocking {
        val api = mockCloudflareApi { get("/zones/z1") { DemoReply.failure(HttpStatusCode.InternalServerError, 1, "boom") } }
        assertTrue((api.get<Zone>("/zones/z1") as ApiResult.Failure).error is ApiError.Server)
    }

    @Test
    fun `download streams the body to the consumer`() = runBlocking {
        val api = mockCloudflareApi { get("/accounts/a/r2/buckets/b/objects/k") { DemoReply.raw("abc".encodeToByteArray()) } }
        val result = api.download("/accounts/a/r2/buckets/b/objects/k") { channel, length ->
            Pair(channel.readRemaining().readText(), length)
        }
        assertEquals("abc", (result as ApiResult.Success).value.first)
        assertEquals(3L, result.value.second)
    }

    @Test
    fun `download failure is mapped and the consumer is not called`() = runBlocking {
        val api = mockCloudflareApi { get("/x") { DemoReply.failure(HttpStatusCode.NotFound, 10007, "missing") } }
        var consumed = false
        val result = api.download("/x") { _, _ -> consumed = true }
        assertTrue((result as ApiResult.Failure).error is ApiError.NotFound)
        assertFalse(consumed)
    }

    @Test
    fun `streaming upload sends the known length and the content type`() = runBlocking {
        var length: Long? = null
        var type: String? = null
        var payload = ""
        val api = CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            liveEngine = MockEngine { request ->
                val content = request.body
                length = content.contentLength
                type = content.contentType?.withoutParameters()?.toString()
                payload = readOutgoingBody(content)
                respond(envelopeOk, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val data = "hello".encodeToByteArray()
        api.uploadStream(
            method = HttpMethod.Put,
            path = "/accounts/a/r2/buckets/b/objects/k",
            contentType = ContentType.Application.OctetStream,
            contentLength = data.size.toLong(),
            write = { channel -> channel.writeFully(data) },
            deserializer = Zone.serializer(),
        )
        assertEquals(5L, length)
        assertEquals("application/octet-stream", type)
        assertEquals("hello", payload)
    }

    @Test
    fun `multipart upload sends the text and file parts`() = runBlocking {
        var payload = ""
        val api = CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            liveEngine = MockEngine { request ->
                payload = readOutgoingBody(request.body)
                respond(envelopeOk, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        api.postMultipart(
            path = "/zones/z1/dns_records/import",
            parts = listOf(
                FormPart.Text("proxied", "true"),
                FormPart.File("file", "records.txt", ContentType.Text.Plain, "a 1.2.3.4".encodeToByteArray()),
            ),
            deserializer = Zone.serializer(),
        )
        assertTrue(payload, payload.contains("name=\"proxied\""))
        assertTrue(payload, payload.contains("filename=\"records.txt\""))
        assertTrue(payload, payload.contains("a 1.2.3.4"))
    }

    @Test
    fun `getText returns a plain-text body`() = runBlocking {
        val api = mockCloudflareApi { get("/logs") { DemoReply.text("line one\nline two") } }
        assertEquals("line one\nline two", (api.getText("/logs") as ApiResult.Success).value)
    }

    @Test
    fun `demo mode serves demo routes and never touches the live engine`() = runBlocking {
        var liveCalls = 0
        val demo = DemoMode(initiallyEnabled = true)
        val api = CloudflareApi.create(
            credentials = CredentialProvider { null },
            demoMode = demo,
            demoRoutes = DemoRouteRegistry().apply { get("/zones/z1") { DemoReply.ok(buildJsonObject { put("id", "z1"); put("name", "demo.dev") }) } },
            liveEngine = MockEngine { liveCalls++; respond("", HttpStatusCode.OK) },
        )
        val result = api.get<Zone>("/zones/z1")
        assertEquals("demo.dev", (result as ApiResult.Success).value.name)
        assertEquals(0, liveCalls)
    }

    @Test
    fun `logger receives the method path and status but never headers`() = runBlocking {
        val lines = mutableListOf<String>()
        val api = CloudflareApi.create(
            credentials = CredentialProvider { TEST_TOKEN },
            liveEngine = MockEngine { respond(envelopeOk, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
            logger = ApiLogger { lines += it },
        )
        api.get<Zone>("/zones/z1")
        assertEquals(listOf("GET /zones/z1 -> 200"), lines)
        assertFalse(lines.joinToString().contains(TEST_TOKEN))
    }

    @Test
    fun `result info on a page is exposed on the success`() = runBlocking {
        val api = mockCloudflareApi {
            get("/zones") { DemoReply.ok(kotlinx.serialization.json.JsonArray(emptyList()), buildJsonObject { put("page", 1); put("per_page", 20); put("count", 0); put("total_count", 0) }) }
        }
        val result = api.get<List<Zone>>("/zones") as ApiResult.Success
        assertEquals(PageInfo.Page(page = 1, perPage = 20, count = 0, totalCount = 0), result.pageInfo)
    }
}

