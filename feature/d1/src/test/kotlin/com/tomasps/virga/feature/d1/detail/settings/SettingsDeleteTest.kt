package com.tomasps.virga.feature.d1.detail.settings

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val DB = "db-1"
private const val ONE = "/accounts/{accountId}/d1/database/{databaseId}"
private const val WAIT_MS = 5_000L
private const val NOW = 1_000_000L

class SettingsDeleteTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `a database deleted from settings is remembered so it can be restored`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            get(ONE) { DemoReply.ok(buildJsonObject { put("uuid", DB); put("name", "orders") }) }
            delete(ONE) { DemoReply.empty() }
        }
        val log = InMemoryRecentlyDeletedLog()
        val viewModel = settings(api, log)
        withTimeout(WAIT_MS) { viewModel.state.first { it.database is UiState.Content } }

        viewModel.requestDelete()
        viewModel.confirmDelete()
        withTimeout(WAIT_MS) { viewModel.state.first { it.isDeleted } }

        val remembered = log.current(ACCOUNT).single()
        assertEquals(DB, remembered.uuid)
        assertEquals("orders", remembered.name)
        assertEquals(NOW, remembered.deletedAtMillis)
    }

    @Test
    fun `a refused delete from settings is not remembered`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            get(ONE) { DemoReply.ok(buildJsonObject { put("uuid", DB); put("name", "orders") }) }
            delete(ONE) { DemoReply.failure(io.ktor.http.HttpStatusCode.Forbidden, 10000, "no") }
        }
        val log = InMemoryRecentlyDeletedLog()
        val viewModel = settings(api, log)
        withTimeout(WAIT_MS) { viewModel.state.first { it.database is UiState.Content } }

        viewModel.requestDelete()
        viewModel.confirmDelete()
        withTimeout(WAIT_MS) { viewModel.state.first { it.actionError != null } }

        assertTrue(log.current(ACCOUNT).isEmpty())
    }

    private fun settings(api: com.tomasps.virga.core.network.CloudflareApi, log: InMemoryRecentlyDeletedLog) = DatabaseSettingsViewModel(
        session = FakeSessionRepository(
            SessionState.SignedIn(listOf(CloudflareAccount(id = ACCOUNT, name = ACCOUNT)), selectedAccountId = ACCOUNT, isDemo = false),
        ),
        repository = DatabasesRepository(api),
        recentlyDeleted = log,
        databaseId = DB,
        nowMillis = { NOW },
    )
}
