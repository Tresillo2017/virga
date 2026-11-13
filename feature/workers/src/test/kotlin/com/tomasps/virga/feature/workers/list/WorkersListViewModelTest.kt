package com.tomasps.virga.feature.workers.list

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.workers.data.WorkerScript
import com.tomasps.virga.feature.workers.data.WorkersRepository
import com.tomasps.virga.feature.workers.testing.FakeSessionRepository
import com.tomasps.virga.feature.workers.testing.MainDispatcherRule
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import com.tomasps.virga.feature.workers.testing.NoBuildsGateway
import com.tomasps.virga.feature.workers.testing.assertIs

private const val WAIT_MS = 2_000L

class WorkersListViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun script(id: String, modified: String) = WorkerScript(id = id, modifiedOn = modified)

    @Test
    fun `visible scripts match the query ignoring case and sort by name`() {
        val state = WorkersListUiState(
            query = "EDGE",
            sort = WorkerSort.NAME,
            scripts = UiState.Content(
                listOf(script("cron-cleanup", "2026-01-01"), script("edge-api", "2026-01-02"), script("edge-cache", "2026-01-03")),
            ),
        )

        assertEquals(listOf("edge-api", "edge-cache"), state.visibleScripts().map { it.id })
    }

    @Test
    fun `visible scripts sort the most recently modified first`() {
        val state = WorkersListUiState(
            sort = WorkerSort.MODIFIED,
            scripts = UiState.Content(listOf(script("a", "2026-01-01"), script("b", "2026-03-01"))),
        )

        assertEquals(listOf("b", "a"), state.visibleScripts().map { it.id })
    }

    @Test
    fun `scripts load for the selected account and the list shows them`() = runBlocking {
        val session = FakeSessionRepository()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") { request ->
                assertEquals("acc-1", request.pathParams["accountId"])
                DemoReply.ok(buildJsonArray { add(buildJsonObject { put("id", "edge-api") }) })
            }
        }
        val viewModel = WorkersListViewModel(session, WorkersRepository(api), NoBuildsGateway)

        session.signedInAs("acc-1")
        val loaded = withTimeout(WAIT_MS) {
            viewModel.state.first { it.scripts is UiState.Content }
        }

        assertEquals("edge-api", (loaded.scripts as UiState.Content).data.single().id)
    }

    @Test
    fun `a failed list load shows an error state with retry`() = runBlocking {
        val session = FakeSessionRepository()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") {
                DemoReply.failure(HttpStatusCode.Forbidden, code = 10000, message = "denied")
            }
        }
        val viewModel = WorkersListViewModel(session, WorkersRepository(api), NoBuildsGateway)

        session.signedInAs("acc-1")
        val failed = withTimeout(WAIT_MS) {
            viewModel.state.first { it.scripts is UiState.Failure }
        }

        assertIs<UiState.Failure>(failed.scripts)
    }

    @Test
    fun `no list is shown while signed out`() = runBlocking {
        val session = FakeSessionRepository()
        val api = mockCloudflareApi { }
        val viewModel = WorkersListViewModel(session, WorkersRepository(api), NoBuildsGateway)

        assertEquals(null, viewModel.state.value.scripts)
    }
}
