package com.tomasps.virga.feature.pages.list

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L

class PagesListViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val accounts = listOf(CloudflareAccount("a1", "Alpha"), CloudflareAccount("a2", "Beta"))

    private fun projectsFor(accountId: String) = mockCloudflareApi {
        get("/accounts/{accountId}/pages/projects") { request ->
            DemoReply.ok(buildJsonArray {
                add(buildJsonObject { put("id", "x"); put("name", "site-of-$accountId") })
            })
        }
    }

    @Test
    fun `signed out shows no project list at all`() {
        val viewModel = PagesListViewModel(
            FakeSessionRepository(SessionState.SignedOut),
            PagesProjectsRepository(projectsFor("a1")),
        )

        assertNull(viewModel.state.value.projects)
    }

    @Test
    fun `a signed-in session loads the selected account's projects`() = runBlocking {
        val session = FakeSessionRepository(SessionState.SignedIn(accounts, "a1", isDemo = false))
        val viewModel = PagesListViewModel(session, PagesProjectsRepository(projectsFor("a1")))

        val loaded = withTimeout(WAIT_MS) { viewModel.state.first { it.projects is UiState.Content } }

        assertEquals(listOf("site-of-a1"), (loaded.projects as UiState.Content).data.map { it.name })
    }

    @Test
    fun `switching accounts reloads the projects for the new account`() = runBlocking {
        val session = FakeSessionRepository(SessionState.SignedIn(accounts, "a1", isDemo = false))
        val viewModel = PagesListViewModel(session, PagesProjectsRepository(projectsFor("a2")))
        session.emit(SessionState.SignedIn(accounts, "a2", isDemo = false))

        val loaded = withTimeout(WAIT_MS) { viewModel.state.first { it.accountId == "a2" && it.projects is UiState.Content } }

        assertEquals(listOf("site-of-a2"), (loaded.projects as UiState.Content).data.map { it.name })
    }

    @Test
    fun `the search query is kept in state and does not trigger a request`() = runBlocking {
        var requests = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects") { requests++; DemoReply.ok(JsonArray(emptyList())) }
        }
        val session = FakeSessionRepository(SessionState.SignedIn(accounts, "a1", isDemo = false))
        val viewModel = PagesListViewModel(session, PagesProjectsRepository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.projects is UiState.Content } }
        val before = requests

        viewModel.onQueryChange("docs")

        assertEquals("docs", viewModel.state.value.query)
        assertEquals(before, requests)
        assertTrue(requests >= 1)
    }
}
