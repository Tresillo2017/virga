package com.tomasps.virga.feature.pages.domains

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.pages.data.PagesDomainsRepository
import com.tomasps.virga.feature.pages.testing.FakeSessionRepository
import com.tomasps.virga.feature.pages.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L

class DomainsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val session = FakeSessionRepository(
        SessionState.SignedIn(listOf(CloudflareAccount("a1", "Alpha")), "a1", isDemo = false),
    )

    private fun domainJson(name: String, status: String) = buildJsonObject {
        put("id", name); put("name", name); put("status", status)
    }

    @Test
    fun `lists the project's domains`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/domains") {
                DemoReply.ok(buildJsonArray { add(domainJson("docs.lantern.example", "active")) })
            }
        }
        val viewModel = DomainsViewModel("lantern-docs", session, PagesDomainsRepository(api))

        val loaded = withTimeout(WAIT_MS) { viewModel.state.first { it.domains is UiState.Content } }

        assertEquals(listOf("docs.lantern.example"), (loaded.domains as UiState.Content).data.map { it.name })
    }

    @Test
    fun `an invalid hostname is rejected locally and no request is made`() = runBlocking {
        var posts = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/domains") { DemoReply.ok(JsonArray(emptyList())) }
            post("/accounts/{accountId}/pages/projects/{projectName}/domains") { posts++; DemoReply.ok(domainJson("x.example", "pending")) }
        }
        val viewModel = DomainsViewModel("lantern-docs", session, PagesDomainsRepository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.domains is UiState.Content } }

        viewModel.add("not a domain")

        assertNotNull(viewModel.state.value.addError)
        assertEquals(0, posts)
    }

    @Test
    fun `retrying validation replaces that domain in the list`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/domains") {
                DemoReply.ok(buildJsonArray { add(domainJson("docs.lantern.example", "pending")) })
            }
            patch("/accounts/{accountId}/pages/projects/{projectName}/domains/{domainName}") {
                DemoReply.ok(domainJson("docs.lantern.example", "active"))
            }
        }
        val viewModel = DomainsViewModel("lantern-docs", session, PagesDomainsRepository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.domains is UiState.Content } }

        viewModel.retryValidation("docs.lantern.example")
        val after = withTimeout(WAIT_MS) {
            viewModel.state.first { it.busyDomain == null && (it.domains as? UiState.Content)?.data?.single()?.status == "active" }
        }

        assertEquals("active", (after.domains as UiState.Content).data.single().status)
    }

    @Test
    fun `a successful add counts once so the form can clear, and a refused add does not`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/domains") { DemoReply.ok(JsonArray(emptyList())) }
            post("/accounts/{accountId}/pages/projects/{projectName}/domains") { request ->
                if (request.bodyText.orEmpty().contains("taken.example")) {
                    DemoReply.failure(io.ktor.http.HttpStatusCode.Conflict, 8000, "Already in use")
                } else {
                    DemoReply.ok(domainJson("docs.lantern.example", "pending"))
                }
            }
        }
        val viewModel = DomainsViewModel("lantern-docs", session, PagesDomainsRepository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.domains is UiState.Content } }

        // Act
        viewModel.add("taken.example")
        withTimeout(WAIT_MS) { viewModel.state.first { !it.adding && it.addError != null } }
        val afterRefusal = viewModel.state.value.addedCount
        viewModel.add("docs.lantern.example")
        val afterSuccess = withTimeout(WAIT_MS) { viewModel.state.first { !it.adding && it.addedCount > 0 } }

        // Assert
        assertEquals(0, afterRefusal)
        assertEquals(1, afterSuccess.addedCount)
    }
}
