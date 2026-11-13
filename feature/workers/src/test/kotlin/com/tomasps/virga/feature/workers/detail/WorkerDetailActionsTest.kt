package com.tomasps.virga.feature.workers.detail

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.WorkersRepository
import com.tomasps.virga.feature.workers.testing.FakeSessionRepository
import com.tomasps.virga.feature.workers.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val WAIT_MS = 2_000L

private class QuietBuildsGateway : BuildsGateway {
    override suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>> =
        ApiResult.Success(emptyList())

    override suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>> = ApiResult.Success(emptyList())
    override fun watchedBuildIds(): Flow<Set<String>> = flowOf(emptySet())
    override fun startWatching(accountId: String, build: BuildSummary) = Unit
    override fun stopWatching(buildUuid: String) = Unit
}

/** Action paths of [WorkerDetailViewModel]: validation, request bodies, double-submit and reload after delete. */
class WorkerDetailActionsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settingsReads = AtomicInteger(0)
    private val secretWrites = AtomicInteger(0)
    private val domainWrites = AtomicInteger(0)
    private var lastDomainBody: String? = null
    private var lastDeploymentBody: String? = null
    private var lastSecretBody: String? = null

    private fun api(gate: CompletableDeferred<Unit>? = null) = mockCloudflareApi {
        get("/accounts/{accountId}/workers/scripts/{scriptName}/settings") {
            settingsReads.incrementAndGet()
            DemoReply.ok(buildJsonObject { put("compatibility_date", "2026-10-01") })
        }
        get("/accounts/{accountId}/workers/scripts/{scriptName}/subdomain") {
            DemoReply.ok(buildJsonObject { put("enabled", false) })
        }
        get("/accounts/{accountId}/workers/domains") { DemoReply.ok(buildJsonArray { }) }
        get("/accounts/{accountId}/workers/scripts/{scriptName}/deployments") {
            DemoReply.ok(buildJsonObject {
                put("deployments", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "dep-active")
                        put("versions", buildJsonArray {
                            add(buildJsonObject { put("version_id", "v-new"); put("percentage", 100.0) })
                        })
                    })
                })
            })
        }
        get("/accounts/{accountId}/workers/scripts/{scriptName}/versions") {
            DemoReply.ok(buildJsonObject { put("items", buildJsonArray { }) })
        }
        put("/accounts/{accountId}/workers/domains") { request ->
            domainWrites.incrementAndGet()
            lastDomainBody = request.bodyText
            DemoReply.ok(buildJsonObject { put("id", "dom-1"); put("hostname", "shop.example.com") })
        }
        post("/accounts/{accountId}/workers/scripts/{scriptName}/deployments") { request ->
            lastDeploymentBody = request.bodyText
            DemoReply.ok(buildJsonObject { put("id", "dep-created") })
        }
        patch("/accounts/{accountId}/workers/scripts/{scriptName}/secrets-bulk") { request ->
            secretWrites.incrementAndGet()
            lastSecretBody = request.bodyText
            gate?.await()
            DemoReply.ok(buildJsonObject { })
        }
        delete("/accounts/{accountId}/workers/scripts/{scriptName}") {
            DemoReply.ok(buildJsonObject { })
        }
    }

    private suspend fun loaded(api: com.tomasps.virga.core.network.CloudflareApi): WorkerDetailViewModel {
        val session = FakeSessionRepository()
        val viewModel = WorkerDetailViewModel(session, WorkersRepository(api), QuietBuildsGateway(), "edge-api")
        session.signedInAs("acc-1")
        withTimeout(WAIT_MS) { viewModel.state.first { it.snapshot is UiState.Content } }
        return viewModel
    }

    @Test
    fun `an invalid hostname is rejected locally and no PUT is sent`() = kotlinx.coroutines.runBlocking {
        val viewModel = loaded(api())

        viewModel.addCustomDomain("not a host")

        assertEquals(UiText.Resource(R.string.workers_domain_invalid), viewModel.state.value.message)
        assertEquals(0, domainWrites.get())
    }

    @Test
    fun `a valid hostname is trimmed, lowercased and sent with the Worker as its service`() = kotlinx.coroutines.runBlocking {
        val viewModel = loaded(api())

        viewModel.addCustomDomain("  Shop.Example.COM ")
        withTimeout(WAIT_MS) { viewModel.state.first { !it.isBusy && it.message != null } }

        assertEquals(1, domainWrites.get())
        assertTrue(lastDomainBody.orEmpty().contains("\"hostname\":\"shop.example.com\""))
        assertTrue(lastDomainBody.orEmpty().contains("\"service\":\"edge-api\""))
    }

    @Test
    fun `a successful Worker delete marks the state deleted and does not reload the snapshot`() = kotlinx.coroutines.runBlocking {
        val viewModel = loaded(api())
        val readsBefore = settingsReads.get()

        viewModel.deleteWorker()
        withTimeout(WAIT_MS) { viewModel.state.first { it.deleted } }

        assertEquals(readsBefore, settingsReads.get())
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `deleting a secret sends its name with a null value`() = kotlinx.coroutines.runBlocking {
        val viewModel = loaded(api())

        viewModel.deleteSecret("OLD_SECRET")
        withTimeout(WAIT_MS) { viewModel.state.first { !it.isBusy && it.message != null } }

        assertTrue(lastSecretBody.orEmpty().contains("\"OLD_SECRET\":null"))
    }

    @Test
    fun `a second action while one is in flight is ignored`() = kotlinx.coroutines.runBlocking {
        val gate = CompletableDeferred<Unit>()
        val viewModel = loaded(api(gate))

        viewModel.saveSecret("FIRST", "one")
        viewModel.saveSecret("SECOND", "two")
        gate.complete(Unit)
        withTimeout(WAIT_MS) { viewModel.state.first { !it.isBusy && it.message != null } }

        assertEquals(1, secretWrites.get())
    }

    @Test
    fun `rollback posts one version at 100 percent against the expected active deployment`() = kotlinx.coroutines.runBlocking {
        val viewModel = loaded(api())

        viewModel.rollback(versionId = "v-old", expectedActiveDeploymentId = "dep-active")
        withTimeout(WAIT_MS) { viewModel.state.first { !it.isBusy && it.message != null } }

        val body = lastDeploymentBody.orEmpty()
        assertTrue(body.contains("\"version_id\":\"v-old\""))
        assertTrue(body.contains("\"percentage\":100.0"))
    }
}
