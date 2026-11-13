package com.tomasps.virga.feature.account.account

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.account.details.AccountDetailsRepository
import com.tomasps.virga.feature.account.testing.FakeSessionRepository
import com.tomasps.virga.feature.account.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L

class AccountHomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val api = mockCloudflareApi {
        get("/accounts/{accountId}") { request ->
            DemoReply.ok(buildJsonObject {
                put("id", request.pathParams["accountId"].orEmpty())
                put("name", "Name of ${request.pathParams["accountId"]}")
                put("type", "standard")
            })
        }
    }

    private val accounts = listOf(CloudflareAccount("a1", "Alpha"), CloudflareAccount("a2", "Beta"))

    @Test
    fun `signed out has no header name and no details`() = runBlocking {
        // Arrange
        val viewModel = AccountHomeViewModel(FakeSessionRepository(SessionState.SignedOut), AccountDetailsRepository(api))

        // Act
        val state = viewModel.state.value

        // Assert
        assertNull(state.accountName)
        assertNull(state.details)
    }

    @Test
    fun `live session loads the selected account's details`() = runBlocking {
        // Arrange
        val session = FakeSessionRepository(SessionState.SignedIn(accounts, "a2", isDemo = false))

        // Act
        val state = awaitState(AccountHomeViewModel(session, AccountDetailsRepository(api))) {
            it.details is UiState.Content
        }

        // Assert
        assertEquals("Beta", state.accountName)
        assertEquals(2, state.accountCount)
        assertEquals("Name of a2", (state.details as UiState.Content).data.name)
        assertEquals("standard", (state.details as UiState.Content).data.type)
    }

    @Test
    fun `switching the selected account reloads details for the new account`() = runBlocking {
        // Arrange
        val session = FakeSessionRepository(SessionState.SignedIn(accounts, "a1", isDemo = false))
        val viewModel = AccountHomeViewModel(session, AccountDetailsRepository(api))
        awaitState(viewModel) { it.details is UiState.Content }

        // Act
        session.emit(SessionState.SignedIn(accounts, "a2", isDemo = false))

        // Assert
        val state = awaitState(viewModel) { (it.details as? UiState.Content)?.data?.id == "a2" }
        assertEquals("a2", (state.details as UiState.Content).data.id)
    }

    @Test
    fun `a session switch cancels a slow load for the previous account`() = runBlocking {
        // Arrange: the first account's details never answer until the gate opens.
        val gate = CompletableDeferred<Unit>()
        val slowApi = mockCloudflareApi {
            get("/accounts/{accountId}") { request ->
                val id = request.pathParams["accountId"].orEmpty()
                if (id == "a1") gate.await()
                DemoReply.ok(buildJsonObject { put("id", id); put("name", "Name of $id"); put("type", "standard") })
            }
        }
        val session = FakeSessionRepository(SessionState.SignedIn(accounts, "a1", isDemo = false))
        val viewModel = AccountHomeViewModel(session, AccountDetailsRepository(slowApi))
        awaitState(viewModel) { it.accountName == "Alpha" }

        // Act: switch while a1 is still loading.
        session.emit(SessionState.SignedIn(accounts, "a2", isDemo = false))

        // Assert: a2 loads even though a1 never finished.
        val state = awaitState(viewModel) { (it.details as? UiState.Content)?.data?.id == "a2" }
        gate.complete(Unit)
        assertEquals("Beta", state.accountName)
    }

    @Test
    fun `demo session is flagged as demo`() = runBlocking {
        // Arrange
        val session = FakeSessionRepository(SessionState.SignedIn(listOf(CloudflareAccount("demo", "Demo")), "demo", isDemo = true))

        // Act
        val state = AccountHomeViewModel(session, AccountDetailsRepository(api)).state.value

        // Assert
        assertTrue(state.isDemo)
        assertFalse(state.details == null)
    }

    private suspend fun awaitState(
        viewModel: AccountHomeViewModel,
        predicate: (AccountHomeUiState) -> Boolean,
    ): AccountHomeUiState = withTimeout(WAIT_MS) { viewModel.state.first(predicate) }
}
