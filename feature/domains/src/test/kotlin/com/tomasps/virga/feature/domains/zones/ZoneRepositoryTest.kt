package com.tomasps.virga.feature.domains.zones

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.domains.dns.DnssecRepository
import com.tomasps.virga.feature.domains.dns.DnssecState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneRepositoryTest {

    @Test
    fun `listAll reads zones filtered by account and decodes name servers`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/zones") { request ->
                assertEquals("acc-1", request.query["account.id"])
                DemoReply.ok(JsonArray(listOf(buildJsonObject {
                    put("id", "z1")
                    put("name", "example.com")
                    put("status", "active")
                    put("name_servers", JsonArray(emptyList()))
                    put("future_field", "ignored")
                })), buildJsonObject { put("page", 1); put("per_page", 50); put("count", 1); put("total_count", 1) })
            }
        }

        // Act
        val result = ZoneRepository(api).listAll("acc-1")

        // Assert
        val zones = (result as ApiResult.Success).value
        assertEquals(listOf("example.com"), zones.map { it.name })
    }

    @Test
    fun `setPaused sends a PATCH with the paused flag to the zone path`() = runBlocking {
        // Arrange
        var sentBody = ""
        val api = mockCloudflareApi {
            patch("/zones/{zoneId}") { request ->
                assertEquals("z1", request.pathParams["zoneId"])
                sentBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("id", "z1"); put("name", "example.com"); put("paused", true) })
            }
        }

        // Act
        val result = ZoneRepository(api).setPaused("z1", paused = true)

        // Assert
        assertEquals("true", kotlinx.serialization.json.Json.parseToJsonElement(sentBody).jsonObject["paused"]!!.jsonPrimitive.content)
        assertTrue((result as ApiResult.Success).value.paused)
    }

    @Test
    fun `purge sends exactly one selector in the body`() = runBlocking {
        // Arrange
        var sentBody = ""
        val api = mockCloudflareApi {
            post("/zones/{zoneId}/purge_cache") { request ->
                sentBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("id", "z1") })
            }
        }

        // Act
        ZoneSettingsRepository(api).purge("z1", PurgeRequest.Hosts(listOf("example.com")))

        // Assert
        val keys = kotlinx.serialization.json.Json.parseToJsonElement(sentBody).jsonObject.keys
        assertEquals(setOf("hosts"), keys)
    }

    @Test
    fun `dnssec is enabled with status active and the DS record is read back`() = runBlocking {
        // Arrange
        var sentBody = ""
        val api = mockCloudflareApi {
            patch("/zones/{zoneId}/dnssec") { request ->
                sentBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("status", "active"); put("ds", "example.com. DS 2371") })
            }
        }

        // Act
        val result = DnssecRepository(api).setEnabled("z1", enabled = true)

        // Assert
        assertEquals("active", kotlinx.serialization.json.Json.parseToJsonElement(sentBody).jsonObject["status"]!!.jsonPrimitive.content)
        val details = (result as ApiResult.Success).value
        assertEquals(DnssecState.ACTIVE, details.state)
        assertEquals("example.com. DS 2371", details.ds)
    }

    @Test
    fun `unknown dnssec status maps to UNKNOWN instead of failing`() {
        // Arrange / Act / Assert
        assertEquals(DnssecState.UNKNOWN, DnssecState.fromWire("some-new-state"))
        assertEquals(DnssecState.PENDING_DISABLED, DnssecState.fromWire("pending-disabled"))
    }

    @Test
    fun `a zone write that succeeds signals a change and a refused one does not`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            patch("/zones/{zoneId}") { DemoReply.failure(io.ktor.http.HttpStatusCode.Forbidden, 9109, "Forbidden") }
            delete("/zones/{zoneId}") { DemoReply.ok(buildJsonObject { put("id", "z1") }) }
        }
        val repository = ZoneRepository(api)
        val signals = mutableListOf<Unit>()
        val job = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            repository.changed.collect { signals += Unit }
        }

        // Act
        repository.setPaused("z1", paused = true)
        val afterRefusal = signals.size
        repository.delete("z1")

        // Assert
        assertEquals(0, afterRefusal)
        assertEquals(1, signals.size)
        job.cancel()
    }
}
