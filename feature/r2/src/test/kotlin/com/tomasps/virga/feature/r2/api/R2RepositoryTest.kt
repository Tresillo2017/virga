package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val READ_BUFFER_BYTES = 1024

private suspend fun readAll(channel: io.ktor.utils.io.ByteReadChannel): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_BYTES)
    while (true) {
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read == -1) break
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

class R2RepositoryTest {

    @Test
    fun `bucket list sends the page size, name filter and cursor, and reads the next cursor`() = runBlocking {
        // Arrange
        var seenQuery: Map<String, String> = emptyMap()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets") { request ->
                seenQuery = request.query
                DemoReply.ok(
                    result = buildJsonObject {
                        put("buckets", buildJsonArray {
                            add(buildJsonObject { put("name", "alpha"); put("location", "enam") })
                        })
                    },
                    resultInfo = buildJsonObject { put("cursor", "alpha") },
                )
            }
        }

        // Act
        val result = R2Repository(api).listBuckets(ACCOUNT, cursor = "start", nameContains = "al")

        // Assert
        val page = (result as ApiResult.Success).value
        assertEquals(listOf("alpha"), page.buckets.map { it.name })
        assertEquals("alpha", page.nextCursor)
        assertEquals("50", seenQuery["per_page"])
        assertEquals("start", seenQuery["cursor"])
        assertEquals("al", seenQuery["name_contains"])
    }

    @Test
    fun `create bucket posts the name, location hint and storage class`() = runBlocking {
        // Arrange
        var seenBody = ""
        val api = mockCloudflareApi {
            post("/accounts/{accountId}/r2/buckets") { request ->
                seenBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("name", "new-bucket") })
            }
        }

        // Act
        val result = R2Repository(api).createBucket(ACCOUNT, "new-bucket", location = "weur", storageClass = "Standard")

        // Assert
        assertTrue(result is ApiResult.Success)
        assertTrue(seenBody.contains("\"name\":\"new-bucket\""))
        assertTrue(seenBody.contains("\"locationHint\":\"weur\""))
        assertTrue(seenBody.contains("\"storageClass\":\"Standard\""))
    }

    @Test
    fun `object keys keep their slashes literal in the path`() = runBlocking {
        // Arrange
        var seenKey: String? = null
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { request ->
                seenKey = request.pathParams["objectKey"]
                DemoReply.raw("hello".encodeToByteArray())
            }
        }

        // Act
        val result = R2Repository(api).downloadObject(ACCOUNT, "photos", "2026/a.png") { channel, _ ->
            readAll(channel)
        }

        // Assert
        assertEquals("2026/a.png", seenKey)
        assertEquals("hello", (result as ApiResult.Success).value)
    }

    @Test
    fun `object listing reads folders from result_info and the cursor when truncated`() = runBlocking {
        // Arrange
        var seenQuery: Map<String, String> = emptyMap()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") { request ->
                seenQuery = request.query
                DemoReply.ok(
                    result = buildJsonArray {
                        add(buildJsonObject { put("key", "docs/readme.md"); put("size", 10) })
                    },
                    resultInfo = buildJsonObject {
                        put("cursor", "c2")
                        put("is_truncated", true)
                        put("delimited", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("docs/img/")) })
                    },
                )
            }
        }

        // Act
        val page = (R2Repository(api).listObjects(ACCOUNT, "site", prefix = "docs/", delimiter = true, cursor = null) as ApiResult.Success).value

        // Assert
        assertEquals("/", seenQuery["delimiter"])
        assertEquals("docs/", seenQuery["prefix"])
        assertEquals(listOf("docs/readme.md"), page.objects.map { it.key })
        assertEquals(listOf("docs/img/"), page.folders)
        assertEquals("c2", page.nextCursor)
    }

    @Test
    fun `search without a delimiter sends no delimiter parameter`() = runBlocking {
        // Arrange
        var seenQuery: Map<String, String> = emptyMap()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") { request ->
                seenQuery = request.query
                DemoReply.ok(buildJsonArray {})
            }
        }

        // Act
        R2Repository(api).listObjects(ACCOUNT, "site", prefix = "logs/2026", delimiter = false, cursor = null)

        // Assert
        assertEquals(null, seenQuery["delimiter"])
        assertEquals("logs/2026", seenQuery["prefix"])
    }

    @Test
    fun `an admin-scope refusal keeps the Cloudflare code so the UI can explain the permission`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            delete("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") {
                DemoReply.failure(HttpStatusCode.Unauthorized, 10002, "not authorized")
            }
        }

        // Act
        val result = R2Repository(api).deleteObject(ACCOUNT, "site", "a.txt")

        // Assert
        val error = (result as ApiResult.Failure).error
        assertTrue(error is ApiError.Unauthorized)
        assertEquals(listOf(10002), error.codes)
    }

    @Test
    fun `replacing CORS rules sends origins, methods and max age`() = runBlocking {
        // Arrange
        var seenBody = ""
        val api = mockCloudflareApi {
            put("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { request ->
                seenBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject {})
            }
        }
        val rule = R2CorsRule(
            allowed = R2CorsAllowed(origins = listOf("https://example.com"), methods = listOf("GET")),
            maxAgeSeconds = 600,
        )

        // Act
        R2Repository(api).replaceCorsRules(ACCOUNT, "site", listOf(rule))

        // Assert
        assertTrue(seenBody.contains("\"origins\":[\"https://example.com\"]"))
        assertTrue(seenBody.contains("\"methods\":[\"GET\"]"))
        assertTrue(seenBody.contains("\"maxAgeSeconds\":600"))
    }

    @Test
    fun `lock rules omit an empty prefix and encode an indefinite condition`() = runBlocking {
        // Arrange
        var seenBody = ""
        val api = mockCloudflareApi {
            put("/accounts/{accountId}/r2/buckets/{bucketName}/lock") { request ->
                seenBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject {})
            }
        }

        // Act
        R2Repository(api).replaceLockRules(
            ACCOUNT,
            "site",
            listOf(R2LockRule(id = "keep", prefix = null, condition = R2LockCondition(type = "Indefinite"))),
        )

        // Assert
        assertTrue(seenBody.contains("\"condition\":{\"type\":\"Indefinite\"}"))
        assertTrue(!seenBody.contains("\"prefix\""))
    }

    @Test
    fun `upload streams the bytes as one PUT with the exact length`() = runBlocking {
        // Arrange
        var seenBytes = 0
        val api = mockCloudflareApi {
            put("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { request ->
                seenBytes = request.bodyBytes?.size ?: 0
                DemoReply.ok(buildJsonObject {})
            }
        }
        val payload = "0123456789".encodeToByteArray()

        // Act
        val result = R2Repository(api).uploadObject(ACCOUNT, "site", "a.bin", "application/octet-stream", payload.size.toLong()) { channel ->
            channel.writeFully(payload, 0, payload.size)
        }

        // Assert
        assertTrue(result is ApiResult.Success)
        assertEquals(payload.size, seenBytes)
    }
}
