package com.tomasps.virga.feature.pages.deployments

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.pages.data.PagesDeploymentsRepository
import com.tomasps.virga.feature.pages.testing.FakeSessionRepository
import com.tomasps.virga.feature.pages.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L

class DeploymentsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val session = FakeSessionRepository(
        SessionState.SignedIn(listOf(CloudflareAccount("a1", "Alpha")), "a1", isDemo = false),
    )

    private fun deploymentJson(id: String) = buildJsonObject { put("id", id); put("environment", "production") }

    @Test
    fun `first page loads and reports more pages when the total is larger`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/deployments") { request ->
                val page = request.query["page"]?.toInt() ?: 1
                DemoReply.ok(
                    buildJsonArray { add(deploymentJson("d$page")) },
                    buildJsonObject { put("page", page); put("per_page", 25); put("total_count", 30) },
                )
            }
        }
        val viewModel = DeploymentsViewModel("lantern-docs", session, PagesDeploymentsRepository(api))

        val loaded = withTimeout(WAIT_MS) { viewModel.state.first { it.deployments is UiState.Content } }

        assertEquals(listOf("d1"), (loaded.deployments as UiState.Content).data.map { it.id })
        assertTrue(loaded.hasMore)
    }

    @Test
    fun `loading more appends the next page and stops when the total is reached`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/deployments") { request ->
                val page = request.query["page"]?.toInt() ?: 1
                DemoReply.ok(
                    if (page == 1) buildJsonArray { add(deploymentJson("d1")) } else buildJsonArray { add(deploymentJson("d2")) },
                    buildJsonObject { put("page", page); put("per_page", 25); put("total_count", 30) },
                )
            }
        }
        val viewModel = DeploymentsViewModel("lantern-docs", session, PagesDeploymentsRepository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.deployments is UiState.Content } }

        viewModel.loadMore()
        val after = withTimeout(WAIT_MS) {
            viewModel.state.first { (it.deployments as? UiState.Content)?.data?.size == 2 && !it.isLoadingMore }
        }
        assertEquals(listOf("d1", "d2"), (after.deployments as UiState.Content).data.map { it.id })
        assertTrue(!after.hasMore)
    }

    @Test
    fun `an empty list is content with no more pages`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/deployments") { DemoReply.ok(JsonArray(emptyList())) }
        }
        val viewModel = DeploymentsViewModel("lantern-docs", session, PagesDeploymentsRepository(api))

        val loaded = withTimeout(WAIT_MS) { viewModel.state.first { it.deployments is UiState.Content } }

        assertTrue((loaded.deployments as UiState.Content).data.isEmpty())
        assertTrue(!loaded.hasMore)
    }

    @Test
    fun `refreshing the first page keeps the older pages already loaded`() = runBlocking {
        var firstPageIds = listOf("d1")
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/deployments") { request ->
                val page = request.query["page"]?.toInt() ?: 1
                val ids = if (page == 1) firstPageIds else listOf("d2")
                DemoReply.ok(
                    buildJsonArray { ids.forEach { add(deploymentJson(it)) } },
                    buildJsonObject { put("page", page); put("per_page", 25); put("total_count", 30) },
                )
            }
        }
        val viewModel = DeploymentsViewModel("lantern-docs", session, PagesDeploymentsRepository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.deployments is UiState.Content } }
        viewModel.loadMore()
        withTimeout(WAIT_MS) { viewModel.state.first { (it.deployments as? UiState.Content)?.data?.size == 2 && !it.isLoadingMore } }

        firstPageIds = listOf("d0", "d1")
        viewModel.refresh()
        val refreshed = withTimeout(WAIT_MS) {
            viewModel.state.first { (it.deployments as? UiState.Content)?.data?.size == 3 && !it.isRefreshing }
        }

        assertEquals(listOf("d0", "d1", "d2"), (refreshed.deployments as UiState.Content).data.map { it.id })
        assertTrue(!refreshed.hasMore)
    }
}
