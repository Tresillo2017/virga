package com.tomasps.virga.feature.r2.settings

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.testing.FakeSessionRepository
import com.tomasps.virga.feature.r2.testing.MainDispatcherRule
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val BUCKET = "site"
private const val LIFECYCLE_PATH = "/accounts/{accountId}/r2/buckets/{bucketName}/lifecycle"
private const val DATE_RULE = """{"id":"dated","enabled":true,"conditions":{"prefix":"old/"},
    "deleteObjectsTransition":{"condition":{"type":"Date","date":"2027-01-01"}}}"""

class R2LifecycleViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `editing the prefix of a date-based rule keeps its expiry date in the written rule`() = runBlocking {
        // Arrange
        var putBody: JsonObject? = null
        var served = rulesOf(DATE_RULE)
        val api = mockCloudflareApi {
            get(LIFECYCLE_PATH) { DemoReply.ok(served) }
            put(LIFECYCLE_PATH) { request ->
                putBody = Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject
                served = requireNotNull(putBody)
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = R2LifecycleViewModel(FakeSessionRepository(), R2Repository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.rules is UiState.Content } }

        // Act
        viewModel.openRule("dated")
        viewModel.onPrefixChanged("archive/")
        viewModel.submit()
        // Wait for the reload that follows the save, so no request is still running when the test ends.
        withTimeout(WAIT_MS) {
            viewModel.state.first { (it.rules as? UiState.Content)?.data?.firstOrNull()?.toLifecycleEntry()?.prefix == "archive/" }
        }

        // Assert
        val written = (putBody?.get("rules") as JsonArray).single().jsonObject
        assertEquals("archive/", written["conditions"]!!.jsonObject["prefix"]!!.jsonPrimitive.content)
        assertEquals(
            Json.parseToJsonElement(DATE_RULE).jsonObject["deleteObjectsTransition"],
            written["deleteObjectsTransition"],
        )
    }

    @Test
    fun `saving a lifecycle rule while the rules failed to load sends nothing`() = runBlocking {
        // Arrange
        var puts = 0
        val api = mockCloudflareApi {
            get(LIFECYCLE_PATH) { DemoReply.failure(HttpStatusCode.InternalServerError, 1000, "boom") }
            put(LIFECYCLE_PATH) {
                puts++
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = R2LifecycleViewModel(FakeSessionRepository(), R2Repository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.rules is UiState.Failure } }

        // Act
        viewModel.openNewRule()
        viewModel.onIdChanged("new-rule")
        viewModel.submit()

        // Assert
        assertEquals(0, puts)
        assertEquals(UiText.Resource(R.string.r2_settings_reload_first), viewModel.state.value.message)
        assertNotNull(viewModel.state.value.form)
    }

    private fun rulesOf(vararg rules: String): JsonObject = buildJsonObject {
        put("rules", JsonArray(rules.map { Json.parseToJsonElement(it) }))
    }
}
