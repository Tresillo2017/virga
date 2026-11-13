package com.tomasps.virga.feature.pages.deployments

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.pages.data.PagesDeploymentsRepository
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import com.tomasps.virga.feature.pages.testing.FakeSessionRepository
import com.tomasps.virga.feature.pages.testing.RealMainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L

/** Short real interval so the polling chain runs on the real clock; the mock engine answers on real time too. */
private const val TEST_POLL_MS = 20L

class DeploymentDetailViewModelTest {

    @get:Rule
    val mainDispatcher = RealMainDispatcherRule()

    private val session = FakeSessionRepository(
        SessionState.SignedIn(listOf(CloudflareAccount("a1", "Alpha")), "a1", isDemo = false),
    )

    /** Returns "active" for the first [activePolls] reads, then "success". The project names [productionId]. */
    private fun deploymentApi(activePolls: Int, productionId: String = "dep-9", logLines: Int = 0) = run {
        var reads = 0
        mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}/history/logs") {
                DemoReply.ok(buildJsonObject {
                    put("data", buildJsonArray {
                        for (index in 1..logLines) add(buildJsonObject { put("line", "line $index") })
                    })
                })
            }
            get("/accounts/{accountId}/pages/projects/{projectName}") {
                DemoReply.ok(buildJsonObject {
                    put("name", "lantern-docs")
                    put("canonical_deployment", buildJsonObject { put("id", productionId) })
                })
            }
            get("/accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}") {
                reads++
                val status = if (reads <= activePolls) "active" else "success"
                DemoReply.ok(buildJsonObject {
                    put("id", "dep-1")
                    put("latest_stage", buildJsonObject { put("name", "build"); put("status", status) })
                    put("stages", buildJsonArray { add(buildJsonObject { put("name", "build"); put("status", status) }) })
                })
            }
        }
    }

    private fun viewModelFor(api: CloudflareApi) = DeploymentDetailViewModel(
        projectName = "lantern-docs",
        deploymentId = "dep-1",
        session = session,
        repository = PagesDeploymentsRepository(api),
        projects = PagesProjectsRepository(api),
        pollIntervalMs = TEST_POLL_MS,
    )

    @Test
    fun `reads the project production deployment so the screen can tell which one is live`() = runBlocking {
        val viewModel = viewModelFor(deploymentApi(activePolls = 0, productionId = "dep-live"))

        val known = withTimeout(WAIT_MS) { viewModel.state.first { it.productionKnown } }

        assertEquals("dep-live", known.currentProductionId)
    }

    @Test
    fun `keeps only the newest log lines so a long build log stays bounded`() = runBlocking {
        val viewModel = viewModelFor(deploymentApi(activePolls = 0, logLines = MAX_LOG_LINES + 25))
        withTimeout(WAIT_MS) { viewModel.state.first { it.deployment is UiState.Content } }

        viewModel.loadLogs()
        val logs = withTimeout(WAIT_MS) { viewModel.state.first { it.logs is UiState.Content } }.logs as UiState.Content

        assertEquals(MAX_LOG_LINES, logs.data.size)
        assertEquals("line ${MAX_LOG_LINES + 25}", logs.data.last())
    }

    @Test
    fun `polls an in-progress deployment and stops once it reaches a terminal state`() = runBlocking {
        val api = deploymentApi(activePolls = 2)
        val viewModel = viewModelFor(api)
        withTimeout(WAIT_MS) { viewModel.state.first { it.isPolling } }

        val finished = withTimeout(WAIT_MS) { viewModel.state.first { !it.isPolling && it.deployment is UiState.Content } }

        assertFalse(finished.isPolling)
        assertEquals("success", (finished.deployment as UiState.Content).data.latestStage?.status)
    }

    @Test
    fun `a deployment that is already finished is not polled`() = runBlocking {
        val viewModel = viewModelFor(deploymentApi(activePolls = 0))

        val loaded = withTimeout(WAIT_MS) { viewModel.state.first { it.deployment is UiState.Content } }

        assertFalse(loaded.isPolling)
        assertTrue(loaded.deployment is UiState.Content)
    }
}
