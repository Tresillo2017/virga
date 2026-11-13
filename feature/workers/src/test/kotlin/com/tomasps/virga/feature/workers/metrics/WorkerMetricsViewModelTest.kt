package com.tomasps.virga.feature.workers.metrics

import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.workers.testing.FakeSessionRepository
import com.tomasps.virga.feature.workers.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class WorkerMetricsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now = Instant.parse("2026-11-11T12:00:00Z")

    @Test
    fun `loads the default window for the selected account and reports content`() = runBlocking {
        val windows = mutableListOf<Pair<String, String>>()
        val session = FakeSessionRepository(SessionState.SignedOut).apply { signedInAs("acc-1") }
        val viewModel = WorkerMetricsViewModel(session, repository(windows), "edge-api") { now }

        val state = withTimeout(WAIT_MS) { viewModel.state.first { it.content is UiState.Content } }

        assertEquals(TimeRange.TWENTY_FOUR_HOURS, state.range)
        assertEquals("acc-1", state.accountId)
        assertEquals(1, windows.size)
    }

    @Test
    fun `changing the range asks for the matching window`() = runBlocking {
        val windows = mutableListOf<Pair<String, String>>()
        val session = FakeSessionRepository(SessionState.SignedOut).apply { signedInAs("acc-1") }
        val viewModel = WorkerMetricsViewModel(session, repository(windows), "edge-api") { now }
        withTimeout(WAIT_MS) { viewModel.state.first { it.content is UiState.Content } }

        viewModel.selectRange(TimeRange.SEVEN_DAYS)
        withTimeout(WAIT_MS) { viewModel.state.first { it.range == TimeRange.SEVEN_DAYS && it.content is UiState.Content } }

        val (start, end) = windows.last()
        assertEquals(now.minusSeconds(TimeRange.SEVEN_DAYS.seconds).toString(), start)
        assertEquals(now.toString(), end)
    }

    @Test
    fun `a failed query shows a failure state, not empty content`() = runBlocking {
        val api = mockCloudflareApi { post("/graphql") { DemoReply.graphQlErrors("denied") } }
        val session = FakeSessionRepository(SessionState.SignedOut).apply { signedInAs("acc-1") }
        val viewModel = WorkerMetricsViewModel(session, WorkerMetricsRepository(api), "edge-api") { now }

        val state = withTimeout(WAIT_MS) { viewModel.state.first { it.content is UiState.Failure } }

        assertTrue(state.content is UiState.Failure)
    }

    private fun repository(windows: MutableList<Pair<String, String>>): WorkerMetricsRepository {
        val api = mockCloudflareApi {
            post("/graphql") { request ->
                val variables = Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject["variables"] as JsonObject
                windows += variables["datetimeStart"]!!.jsonPrimitive.content to variables["datetimeEnd"]!!.jsonPrimitive.content
                DemoReply.graphQl(Json.parseToJsonElement("""{"viewer":{"accounts":[{"workersInvocationsAdaptive":[]}]}}""").jsonObject)
            }
        }
        return WorkerMetricsRepository(api)
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
