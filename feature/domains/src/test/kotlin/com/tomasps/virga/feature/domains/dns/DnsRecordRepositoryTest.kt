package com.tomasps.virga.feature.domains.dns

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsRecordRepositoryTest {

    private fun record(id: String) = buildJsonObject {
        put("id", id)
        put("name", "$id.example.com")
        put("type", "A")
        put("content", "192.0.2.1")
        put("ttl", 1)
        put("proxied", false)
        put("proxiable", true)
    }

    @Test
    fun `a full page reports more records when the total says so`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/zones/{zoneId}/dns_records") { request ->
                assertEquals("1", request.query["page"])
                assertEquals("2", request.query["per_page"])
                DemoReply.ok(
                    JsonArray(listOf(record("a"), record("b"))),
                    buildJsonObject { put("page", 1); put("per_page", 2); put("count", 2); put("total_count", 5) },
                )
            }
        }

        // Act
        val result = DnsRecordRepository(api).page("z1", page = 1, perPage = 2)

        // Assert
        val page = (result as ApiResult.Success).value
        assertEquals(2, page.records.size)
        assertTrue(page.hasMore)
    }

    @Test
    fun `the type filter is sent to the API`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/zones/{zoneId}/dns_records") { request ->
                assertEquals("MX", request.query["type"])
                DemoReply.ok(JsonArray(emptyList()), buildJsonObject { put("page", 1); put("per_page", 100); put("count", 0); put("total_count", 0) })
            }
        }

        // Act
        val result = DnsRecordRepository(api).page("z1", page = 1, type = "MX")

        // Assert
        assertFalse((result as ApiResult.Success).value.hasMore)
    }

    @Test
    fun `bulk delete uses the batch endpoint with only a deletes array`() = runBlocking {
        // Arrange
        var sent: JsonObject? = null
        val api = mockCloudflareApi {
            post("/zones/{zoneId}/dns_records/batch") { request ->
                sent = Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject
                DemoReply.ok(buildJsonObject { put("deletes", JsonArray(emptyList())) })
            }
        }

        // Act
        DnsRecordRepository(api).deleteBatch("z1", listOf("r1", "r2"))

        // Assert
        val body = sent!!
        assertEquals(setOf("deletes"), body.keys)
        assertEquals(listOf("r1", "r2"), body["deletes"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
    }

    @Test
    fun `proxy toggle sends a partial PATCH with only proxied`() = runBlocking {
        // Arrange
        var sent: JsonObject? = null
        val api = mockCloudflareApi {
            patch("/zones/{zoneId}/dns_records/{recordId}") { request ->
                assertEquals("r9", request.pathParams["recordId"])
                sent = Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject
                DemoReply.ok(record("r9"))
            }
        }

        // Act
        DnsRecordRepository(api).setProxied("z1", "r9", proxied = true)

        // Assert
        assertEquals(setOf("proxied"), sent!!.keys)
    }
}
