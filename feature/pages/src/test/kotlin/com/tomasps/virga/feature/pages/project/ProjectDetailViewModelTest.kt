package com.tomasps.virga.feature.pages.project

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import com.tomasps.virga.feature.pages.testing.FakeSessionRepository
import com.tomasps.virga.feature.pages.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L

class ProjectDetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val session = FakeSessionRepository(
        SessionState.SignedIn(listOf(CloudflareAccount("a1", "Alpha")), "a1", isDemo = false),
    )

    private fun api(onPatch: (String) -> Unit = {}, onDelete: () -> Unit = {}) = mockCloudflareApi {
        get("/accounts/{accountId}/pages/projects/{projectName}") {
            DemoReply.ok(buildJsonObject { put("id", "p"); put("name", "lantern-docs"); put("production_branch", "main") })
        }
        patch("/accounts/{accountId}/pages/projects/{projectName}") { request ->
            onPatch(request.bodyText.orEmpty())
            DemoReply.ok(buildJsonObject { put("id", "p"); put("name", "lantern-docs"); put("production_branch", "release") })
        }
        delete("/accounts/{accountId}/pages/projects/{projectName}") { onDelete(); DemoReply.ok(kotlinx.serialization.json.JsonNull) }
    }

    @Test
    fun `loads the project for the selected account`() = runBlocking {
        val viewModel = ProjectDetailViewModel("lantern-docs", session, PagesProjectsRepository(api()))

        val loaded = withTimeout(WAIT_MS) { viewModel.state.first { it.project is UiState.Content } }

        assertEquals("main", (loaded.project as UiState.Content).data.productionBranch)
    }

    @Test
    fun `saving the production branch sends only that field and shows the returned project`() = runBlocking {
        var sentBody = ""
        val viewModel = ProjectDetailViewModel("lantern-docs", session, PagesProjectsRepository(api(onPatch = { sentBody = it })))
        withTimeout(WAIT_MS) { viewModel.state.first { it.project is UiState.Content } }

        viewModel.saveProductionBranch("release")
        val saved = withTimeout(WAIT_MS) { viewModel.state.first { it.message != null && !it.busy } }

        assertEquals("""{"production_branch":"release"}""", sentBody)
        assertEquals("release", (saved.project as UiState.Content).data.productionBranch)
    }

    @Test
    fun `a successful project delete marks the state as deleted`() = runBlocking {
        var deleteCalls = 0
        val viewModel = ProjectDetailViewModel("lantern-docs", session, PagesProjectsRepository(api(onDelete = { deleteCalls++ })))
        withTimeout(WAIT_MS) { viewModel.state.first { it.project is UiState.Content } }

        viewModel.deleteProject()
        withTimeout(WAIT_MS) { viewModel.state.first { it.deleted } }

        assertEquals(1, deleteCalls)
    }

    @Test
    fun `purging the build cache succeeds without marking the project deleted`() = runBlocking {
        var purgeCalls = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}") {
                DemoReply.ok(buildJsonObject { put("id", "p"); put("name", "lantern-docs") })
            }
            post("/accounts/{accountId}/pages/projects/{projectName}/purge_build_cache") {
                purgeCalls++
                DemoReply.ok(kotlinx.serialization.json.JsonNull)
            }
        }
        val viewModel = ProjectDetailViewModel("lantern-docs", session, PagesProjectsRepository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.project is UiState.Content } }

        viewModel.purgeBuildCache()
        withTimeout(WAIT_MS) { viewModel.state.first { it.message != null && !it.busy } }

        assertEquals(1, purgeCalls)
        assertTrue(!viewModel.state.value.deleted)
    }
}
