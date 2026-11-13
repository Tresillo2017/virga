package com.tomasps.virga.feature.r2.settings

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2Repository
import io.ktor.http.HttpStatusCode
import com.tomasps.virga.feature.r2.testing.FakeSessionRepository
import com.tomasps.virga.feature.r2.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val BUCKET = "site"

class R2CorsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `editing one CORS rule writes the whole list back and keeps the other rules intact`() = runBlocking {
        // Arrange
        var putBody: JsonObject? = null
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { DemoReply.ok(corsConfig()) }
            put("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { request ->
                putBody = Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = R2CorsViewModel(FakeSessionRepository(), R2Repository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.rules is UiState.Content } }

        // Act
        viewModel.openRule(index = 1)
        viewModel.onOriginsChanged("https://second.example.test")
        viewModel.submit()
        withTimeout(WAIT_MS) { viewModel.state.first { it.message != null && it.editor == null } }

        // Assert
        val rules = requireNotNull(putBody?.get("rules")).jsonArray
        assertEquals(2, rules.size)
        assertEquals("https://first.example.test", rules[0].origins())
        assertEquals(listOf("X-First"), rules[0].jsonObject["allowed"]!!.jsonObject["headers"]!!.strings())
        assertEquals(listOf("X-Exposed"), rules[0].jsonObject["exposeHeaders"]!!.strings())
        assertEquals("https://second.example.test", rules[1].origins())
        assertEquals("rule-2", rules[1].jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `adding a CORS rule while the rules failed to load sends no request`() = runBlocking {
        // Arrange
        var puts = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { DemoReply.failure(HttpStatusCode.InternalServerError, 1000, "boom") }
            put("/accounts/{accountId}/r2/buckets/{bucketName}/cors") {
                puts++
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = R2CorsViewModel(FakeSessionRepository(), R2Repository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.rules is UiState.Failure } }

        // Act
        viewModel.openNewRule()
        viewModel.onOriginsChanged("https://new.example.test")
        viewModel.submit()

        // Assert
        assertEquals(0, puts)
        assertEquals(UiText.Resource(R.string.r2_settings_reload_first), viewModel.state.value.message)
    }

    @Test
    fun `saving an edit is refused when the rule changed under the open editor`() = runBlocking {
        // Arrange
        var puts = 0
        var served = corsConfig()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { DemoReply.ok(served) }
            put("/accounts/{accountId}/r2/buckets/{bucketName}/cors") {
                puts++
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = R2CorsViewModel(FakeSessionRepository(), R2Repository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.rules is UiState.Content } }
        viewModel.openRule(index = 1)
        served = buildJsonObject {}
        viewModel.retry()
        withTimeout(WAIT_MS) { viewModel.state.first { it.rules is UiState.Content && (it.rules as UiState.Content).data.isEmpty() } }

        // Act
        viewModel.onOriginsChanged("https://second.example.test")
        viewModel.submit()

        // Assert
        assertEquals(0, puts)
    }

    @Test
    fun `removing one rule writes the remaining rules back`() = runBlocking {
        // Arrange
        var putBody: JsonObject? = null
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { DemoReply.ok(corsConfig()) }
            put("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { request ->
                putBody = Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = R2CorsViewModel(FakeSessionRepository(), R2Repository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.rules is UiState.Content } }

        // Act
        viewModel.requestRemoveRule(index = 0)
        viewModel.confirmRemoveRule()
        withTimeout(WAIT_MS) { viewModel.state.first { !it.isWorking && it.message != null } }

        // Assert
        assertNull(viewModel.state.value.ruleToRemove)
        val remaining = requireNotNull(putBody?.get("rules")).jsonArray
        assertEquals(listOf("rule-2"), remaining.map { it.jsonObject["id"]!!.jsonPrimitive.content })
    }

    private fun corsConfig(): JsonObject = buildJsonObject {
        put("rules", buildJsonArray {
            add(buildJsonObject {
                put("id", "rule-1")
                put("allowed", buildJsonObject {
                    put("origins", buildJsonArray { add(JsonPrimitive("https://first.example.test")) })
                    put("methods", buildJsonArray { add(JsonPrimitive("GET")) })
                    put("headers", buildJsonArray { add(JsonPrimitive("X-First")) })
                })
                put("exposeHeaders", buildJsonArray { add(JsonPrimitive("X-Exposed")) })
                put("maxAgeSeconds", 100)
            })
            add(buildJsonObject {
                put("id", "rule-2")
                put("allowed", buildJsonObject {
                    put("origins", buildJsonArray { add(JsonPrimitive("https://old.example.test")) })
                    put("methods", buildJsonArray { add(JsonPrimitive("GET")) })
                })
            })
        })
    }

    private fun kotlinx.serialization.json.JsonElement.origins(): String =
        jsonObject["allowed"]!!.jsonObject["origins"]!!.jsonArray[0].jsonPrimitive.content

    private fun kotlinx.serialization.json.JsonElement.strings(): List<String> =
        jsonArray.map { it.jsonPrimitive.content }
}
