package com.tomasps.virga.feature.kv.data

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.kv.value.KvExpiration
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val NAMESPACE = "ns-1"
private const val NS_BASE = "/accounts/{accountId}/storage/kv/namespaces"
private const val LOOKUP_PAGES_IN_REPOSITORY = 5

class KvRepositoryTest {

    @Test
    fun `namespaces are read from the storage kv route and decoded`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get(NS_BASE) { request ->
                assertEquals(ACCOUNT, request.pathParams["accountId"])
                DemoReply.ok(buildJsonArray {
                    add(buildJsonObject { put("id", NAMESPACE); put("title", "Cache"); put("extra", true) })
                })
            }
        }

        // Act
        val result = KvRepository(api).namespaces(ACCOUNT)

        // Assert
        val value = (result as ApiResult.Success).value
        assertEquals(listOf(NAMESPACE), value.map { it.id })
        assertEquals("Cache", value.single().title)
    }

    @Test
    fun `legacy workers namespace routes are never called`() = runBlocking {
        // Arrange: only the storage route is registered; a call to the legacy path would return 404
        val api = mockCloudflareApi {
            get(NS_BASE) { DemoReply.ok(buildJsonArray { }) }
        }

        // Act
        val result = KvRepository(api).namespaces(ACCOUNT)

        // Assert
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `key list sends prefix limit and cursor and reads the next cursor from result_info`() = runBlocking {
        // Arrange
        var seenQuery: Map<String, String> = emptyMap()
        val api = mockCloudflareApi {
            get("$NS_BASE/{namespaceId}/keys") { request ->
                seenQuery = request.query
                DemoReply.ok(
                    buildJsonArray {
                        add(buildJsonObject { put("name", "a/b c") ; put("expiration", 1_900_000_000L) })
                    },
                    resultInfo = buildJsonObject { put("cursor", "next-page") },
                )
            }
        }

        // Act
        val result = KvRepository(api).keyPage(ACCOUNT, NAMESPACE, prefix = "a/", cursor = "c0", limit = 50)

        // Assert
        val page = (result as ApiResult.Success).value
        assertEquals("a/", seenQuery["prefix"])
        assertEquals("c0", seenQuery["cursor"])
        assertEquals("50", seenQuery["limit"])
        assertEquals("next-page", page.nextCursor)
        assertEquals("a/b c", page.keys.single().name)
        assertEquals(1_900_000_000L, page.keys.single().expirationEpochSeconds)
    }

    @Test
    fun `key list without a cursor in result_info ends the list`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("$NS_BASE/{namespaceId}/keys") { DemoReply.ok(buildJsonArray { }) }
        }

        // Act
        val page = (KvRepository(api).keyPage(ACCOUNT, NAMESPACE, null, null, 10) as ApiResult.Success).value

        // Assert
        assertNull(page.nextCursor)
        assertTrue(page.keys.isEmpty())
    }

    @Test
    fun `find key returns the exact name, not a longer key that shares the prefix`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("$NS_BASE/{namespaceId}/keys") {
                DemoReply.ok(buildJsonArray {
                    add(buildJsonObject { put("name", "user:1:profile") })
                    add(buildJsonObject { put("name", "user:1") ; put("metadata", buildJsonObject { put("tier", "gold") }) })
                })
            }
        }

        // Act
        val found = (KvRepository(api).findKey(ACCOUNT, NAMESPACE, "user:1") as ApiResult.Success).value

        // Assert
        assertEquals("user:1", found?.name)
        assertEquals("gold", (found?.metadata as JsonObject)["tier"]?.jsonPrimitive?.content)
    }

    @Test
    fun `find key reports unknown, not absent, when the prefix scan runs out of pages`() = runBlocking {
        // Arrange: every page points at another page, so the scan stops before it reaches the end
        var calls = 0
        val api = mockCloudflareApi {
            get("$NS_BASE/{namespaceId}/keys") {
                calls++
                DemoReply.ok(
                    buildJsonArray { add(buildJsonObject { put("name", "other") }) },
                    resultInfo = buildJsonObject { put("cursor", "more") },
                )
            }
        }

        // Act
        val result = KvRepository(api).findKey(ACCOUNT, NAMESPACE, "wanted")

        // Assert
        assertTrue(result is ApiResult.Failure)
        assertEquals(LOOKUP_PAGES_IN_REPOSITORY, calls)
    }

    @Test
    fun `a key with slashes and odd characters is one path segment in the value route`() = runBlocking {
        // Arrange
        var seenPath = ""
        val api = mockCloudflareApi {
            get("$NS_BASE/{namespaceId}/values/{key...}") { request ->
                seenPath = request.path
                DemoReply.raw(byteArrayOf(1))
            }
        }

        // Act
        KvRepository(api).readValue(ACCOUNT, NAMESPACE, "a/b c?#%", maxBytes = 1024)

        // Assert
        val afterValues = seenPath.substringAfter("/values/")
        assertTrue("slash must be encoded: $seenPath", afterValues.contains("%2F"))
        assertFalse("no raw slash may split the key: $seenPath", afterValues.contains("/"))
    }

    @Test
    fun `read value returns raw bytes and reports too large values without reading them`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("$NS_BASE/{namespaceId}/values/{keyName...}") { request ->
                DemoReply.raw(request.pathParams["keyName"].orEmpty().encodeToByteArray())
            }
        }
        val repository = KvRepository(api)

        // Act
        val small = (repository.readValue(ACCOUNT, NAMESPACE, "x/y", maxBytes = 100) as ApiResult.Success).value
        val tooLarge = (repository.readValue(ACCOUNT, NAMESPACE, "x/y", maxBytes = 2) as ApiResult.Success).value

        // Assert
        assertEquals("x/y", small?.decodeToString())
        assertNull(tooLarge)
    }

    @Test
    fun `a missing key read maps to not found`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("$NS_BASE/{namespaceId}/values/{keyName...}") {
                DemoReply.failure(HttpStatusCode.NotFound, 10009, "not found")
            }
        }

        // Act
        val result = KvRepository(api).readValue(ACCOUNT, NAMESPACE, "missing", maxBytes = 100)

        // Assert
        assertTrue((result as ApiResult.Failure).error is ApiError.NotFound)
    }

    @Test
    fun `write value sends a multipart body with the value part and the metadata part`() = runBlocking {
        // Arrange
        var body = ""
        var contentType = ""
        val api = mockCloudflareApi {
            put("$NS_BASE/{namespaceId}/values/{keyName...}") { request ->
                body = request.bodyText.orEmpty()
                contentType = request.query["expiration_ttl"].orEmpty()
                DemoReply.ok(buildJsonObject { })
            }
        }

        // Act
        val result = KvRepository(api).writeValue(
            accountId = ACCOUNT,
            namespaceId = NAMESPACE,
            key = "k",
            value = "hello".encodeToByteArray(),
            metadata = buildJsonObject { put("owner", "team-a") },
            expiration = KvExpiration.Ttl(600),
        )

        // Assert
        assertTrue(result is ApiResult.Success)
        assertTrue(body.contains("name=\"value\""))
        assertTrue(body.contains("hello"))
        assertTrue(body.contains("name=\"metadata\""))
        assertTrue(body.contains("\"owner\":\"team-a\""))
        assertEquals("600", contentType)
    }

    @Test
    fun `write value without metadata omits the metadata part and sends no expiration query`() = runBlocking {
        // Arrange
        var body = ""
        var query: Map<String, String> = emptyMap()
        val api = mockCloudflareApi {
            put("$NS_BASE/{namespaceId}/values/{keyName...}") { request ->
                body = request.bodyText.orEmpty()
                query = request.query
                DemoReply.ok(buildJsonObject { })
            }
        }

        // Act
        KvRepository(api).writeValue(ACCOUNT, NAMESPACE, "k", "v".encodeToByteArray(), null, KvExpiration.None)

        // Assert
        assertFalse(body.contains("name=\"metadata\""))
        assertFalse(query.containsKey("expiration"))
        assertFalse(query.containsKey("expiration_ttl"))
    }

    @Test
    fun `bulk write posts a json array and returns unsuccessful keys`() = runBlocking {
        // Arrange
        var seenBody = ""
        val api = mockCloudflareApi {
            put("$NS_BASE/{namespaceId}/bulk") { request ->
                seenBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject {
                    put("successful_key_count", 1)
                    put("unsuccessful_keys", JsonArray(listOf(JsonPrimitive("bad"))))
                })
            }
        }
        val pairs: JsonArray = buildJsonArray { add(buildJsonObject { put("key", "good"); put("value", "1") }) }

        // Act
        val outcome = (KvRepository(api).bulkWrite(ACCOUNT, NAMESPACE, pairs) as ApiResult.Success).value

        // Assert
        assertTrue(seenBody.startsWith("[") && seenBody.contains("\"key\":\"good\""))
        assertEquals(1, outcome.successfulCount)
        assertEquals(listOf("bad"), outcome.unsuccessfulKeys)
    }

    @Test
    fun `bulk delete with a null result still succeeds with zero counts`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            post("$NS_BASE/{namespaceId}/bulk/delete") { DemoReply.ok(kotlinx.serialization.json.JsonNull) }
        }

        // Act
        val outcome = (KvRepository(api).bulkDelete(ACCOUNT, NAMESPACE, listOf("a"))
            as ApiResult.Success).value

        // Assert
        assertEquals(0, outcome.successfulCount)
        assertTrue(outcome.unsuccessfulKeys.isEmpty())
    }

    @Test
    fun `a 429 on a single key write is returned as rate limited`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            put("$NS_BASE/{namespaceId}/values/{keyName...}") {
                DemoReply.failure(HttpStatusCode.TooManyRequests, 10429, "too many writes")
            }
        }

        // Act
        val result = KvRepository(api).writeValue(ACCOUNT, NAMESPACE, "k", ByteArray(1), null, KvExpiration.None)

        // Assert
        assertTrue((result as ApiResult.Failure).error is ApiError.RateLimited)
    }
}
