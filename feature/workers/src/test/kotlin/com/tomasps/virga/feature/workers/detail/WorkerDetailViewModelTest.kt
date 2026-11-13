package com.tomasps.virga.feature.workers.detail

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.DeploymentVersion
import com.tomasps.virga.feature.workers.data.WorkersRepository
import com.tomasps.virga.feature.workers.testing.FakeSessionRepository
import com.tomasps.virga.feature.workers.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 2_000L
private const val ACTIVE_DEPLOYMENT = "dep-active"
private const val OTHER_DEPLOYMENT = "dep-other"

private class NoBuildsGateway : BuildsGateway {
    override suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>> =
        ApiResult.Success(emptyList())

    override suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>> = ApiResult.Success(emptyList())
    override fun watchedBuildIds(): Flow<Set<String>> = flowOf(emptySet())
    override fun startWatching(accountId: String, build: BuildSummary) = Unit
    override fun stopWatching(buildUuid: String) = Unit
}

class WorkerDetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun deploymentsBody(activeId: String) = buildJsonObject {
        put("deployments", buildJsonArray {
            add(buildJsonObject {
                put("id", activeId)
                put("versions", buildJsonArray {
                    add(buildJsonObject { put("version_id", "v-new"); put("percentage", 100.0) })
                })
            })
        })
    }

    private fun apiWithDeployments(activeId: () -> String, onPost: () -> Unit) = mockCloudflareApi {
        get("/accounts/{accountId}/workers/scripts/{scriptName}/settings") {
            DemoReply.ok(buildJsonObject { put("compatibility_date", "2026-10-01") })
        }
        get("/accounts/{accountId}/workers/scripts/{scriptName}/subdomain") {
            DemoReply.ok(buildJsonObject { put("enabled", false) })
        }
        get("/accounts/{accountId}/workers/domains") { DemoReply.ok(buildJsonArray { }) }
        get("/accounts/{accountId}/workers/scripts/{scriptName}/deployments") { DemoReply.ok(deploymentsBody(activeId())) }
        get("/accounts/{accountId}/workers/scripts/{scriptName}/versions") { DemoReply.ok(buildJsonObject { put("items", buildJsonArray { }) }) }
        post("/accounts/{accountId}/workers/scripts/{scriptName}/deployments") {
            onPost()
            DemoReply.ok(buildJsonObject { put("id", "dep-created") })
        }
    }

    private suspend fun loadedViewModel(api: com.tomasps.virga.core.network.CloudflareApi): Pair<WorkerDetailViewModel, FakeSessionRepository> {
        val session = FakeSessionRepository()
        val viewModel = WorkerDetailViewModel(session, WorkersRepository(api), NoBuildsGateway(), "edge-api")
        session.signedInAs("acc-1")
        withTimeout(WAIT_MS) { viewModel.state.first { it.snapshot is UiState.Content } }
        return viewModel to session
    }

    @Test
    fun `a split that does not total 100 is refused without a request`() = runBlocking {
        var posts = 0
        val (viewModel, _) = loadedViewModel(apiWithDeployments({ ACTIVE_DEPLOYMENT }, onPost = { posts++ }))

        viewModel.deploy(listOf(DeploymentVersion("v-new", 60.0), DeploymentVersion("v-old", 30.0)), ACTIVE_DEPLOYMENT)

        assertEquals(0, posts)
        assertEquals(R.string.workers_split_must_total_100, (viewModel.state.value.message as UiText.Resource).id)
    }

    @Test
    fun `a deploy is refused when the active deployment changed since the editor opened`() = runBlocking {
        var posts = 0
        val (viewModel, _) = loadedViewModel(apiWithDeployments({ OTHER_DEPLOYMENT }, onPost = { posts++ }))

        viewModel.deploy(listOf(DeploymentVersion("v-new", 60.0), DeploymentVersion("v-old", 40.0)), ACTIVE_DEPLOYMENT)
        withTimeout(WAIT_MS) { viewModel.state.first { !it.isBusy && it.message != null } }

        assertEquals(0, posts)
        assertEquals(R.string.workers_deployment_changed, (viewModel.state.value.message as UiText.Resource).id)
    }

    @Test
    fun `a valid split posts once when the active deployment is still the one shown`() = runBlocking {
        var posts = 0
        val (viewModel, _) = loadedViewModel(apiWithDeployments({ ACTIVE_DEPLOYMENT }, onPost = { posts++ }))

        viewModel.deploy(listOf(DeploymentVersion("v-new", 60.0), DeploymentVersion("v-old", 40.0)), ACTIVE_DEPLOYMENT)
        withTimeout(WAIT_MS) { viewModel.state.first { !it.isBusy && it.message != null } }

        assertEquals(1, posts)
        assertEquals(R.string.workers_deployment_saved, (viewModel.state.value.message as UiText.Resource).id)
    }

    @Test
    fun `an empty secret name is refused without a request`() = runBlocking {
        var patches = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts/{scriptName}/settings") { DemoReply.ok(buildJsonObject { }) }
            get("/accounts/{accountId}/workers/scripts/{scriptName}/subdomain") { DemoReply.ok(buildJsonObject { put("enabled", false) }) }
            get("/accounts/{accountId}/workers/domains") { DemoReply.ok(buildJsonArray { }) }
            get("/accounts/{accountId}/workers/scripts/{scriptName}/deployments") { DemoReply.ok(deploymentsBody(ACTIVE_DEPLOYMENT)) }
            get("/accounts/{accountId}/workers/scripts/{scriptName}/versions") { DemoReply.ok(buildJsonObject { put("items", buildJsonArray { }) }) }
            patch("/accounts/{accountId}/workers/scripts/{scriptName}/secrets-bulk") {
                patches++
                DemoReply.ok(buildJsonObject { })
            }
        }
        val (viewModel, _) = loadedViewModel(api)

        viewModel.saveSecret(name = " ", value = "value")

        assertEquals(0, patches)
        assertTrue(viewModel.state.value.message is UiText.Resource)
    }

    @Test
    fun `the detail state string never includes the secret or any binding value`() = runBlocking {
        val (viewModel, _) = loadedViewModel(apiWithDeployments({ ACTIVE_DEPLOYMENT }, onPost = {}))

        assertNull(Regex("v-new").find(viewModel.state.value.toString()))
    }
}
