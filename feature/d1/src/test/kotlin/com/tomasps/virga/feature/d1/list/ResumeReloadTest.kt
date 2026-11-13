package com.tomasps.virga.feature.d1.list

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.testing.FakeSessionRepository
import com.tomasps.virga.feature.d1.testing.InMemoryRecentlyDeletedLog
import com.tomasps.virga.feature.d1.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val LIST = "/accounts/{accountId}/d1/database"
private const val WAIT_MS = 5_000L

class ResumeReloadTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `the first resume after creation does not request the list again`() = runBlocking<Unit> {
        var requests = 0
        val viewModel = list(mockCloudflareApi { get(LIST) { requests++; DemoReply.ok(buildJsonArray { }) } })
        withTimeout(WAIT_MS) { viewModel.state.first { it.databases is UiState.Content } }
        val afterCreate = requests

        viewModel.onResumed()

        assertEquals(afterCreate, requests)
    }

    @Test
    fun `coming back to the list reloads it, so a database deleted on its own screen is gone`() = runBlocking<Unit> {
        var names = listOf("orders-demo", "ledger-demo")
        val api = mockCloudflareApi {
            get(LIST) { DemoReply.ok(buildJsonArray { names.forEach { add(buildJsonObject { put("uuid", it); put("name", it) }) } }) }
        }
        val viewModel = list(api)
        withTimeout(WAIT_MS) { viewModel.state.first { (it.databases as? UiState.Content)?.data?.items?.size == 2 } }
        viewModel.onResumed()
        names = listOf("ledger-demo")

        viewModel.onResumed()

        withTimeout(WAIT_MS) { viewModel.state.first { (it.databases as? UiState.Content)?.data?.items?.size == 1 } }
    }

    private fun list(api: com.tomasps.virga.core.network.CloudflareApi) = D1ListViewModel(
        session = FakeSessionRepository(
            SessionState.SignedIn(listOf(CloudflareAccount(id = "acc-1", name = "acc-1")), selectedAccountId = "acc-1", isDemo = false),
        ),
        repository = DatabasesRepository(api),
        recentlyDeleted = InMemoryRecentlyDeletedLog(),
    )
}
