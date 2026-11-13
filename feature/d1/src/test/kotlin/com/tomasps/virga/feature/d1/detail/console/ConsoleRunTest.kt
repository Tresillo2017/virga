package com.tomasps.virga.feature.d1.detail.console

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.d1.data.SqlRepository
import com.tomasps.virga.feature.d1.sql.QueryHistoryStore
import com.tomasps.virga.feature.d1.testing.FakeSessionRepository
import com.tomasps.virga.feature.d1.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
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
import org.junit.rules.TemporaryFolder

private const val RAW = "/accounts/{accountId}/d1/database/{databaseId}/raw"
private const val WAIT_MS = 5_000L

/** The console runs the statement it checked, and records exactly that statement. */
class ConsoleRunTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `confirming a risky statement runs the checked text, not the editor text typed afterwards`() = runBlocking<Unit> {
        val sent = mutableListOf<String>()
        val viewModel = console(sent)

        viewModel.onSqlChanged("DELETE FROM orders")
        viewModel.run()
        viewModel.onSqlChanged("SELECT 1")
        viewModel.confirmRun()
        awaitIdle(viewModel)

        assertEquals(1, sent.size)
        assertTrue(sent.single().contains("DELETE FROM orders"))
        assertNull(viewModel.state.value.pendingRun)
    }

    @Test
    fun `the confirmation lists the statement that will run`() = runBlocking<Unit> {
        val viewModel = console(mutableListOf())

        viewModel.onSqlChanged("  DROP TABLE orders  ")
        viewModel.run()

        assertEquals("DROP TABLE orders", viewModel.state.value.pendingRun?.sql)
    }

    @Test
    fun `history records the exact statement that ran once, even when run twice`() = runBlocking<Unit> {
        val viewModel = console(mutableListOf())
        val statement = "SELECT id,\n  name FROM customers"

        viewModel.onSqlChanged(statement)
        viewModel.run()
        awaitIdle(viewModel)
        viewModel.run()
        awaitIdle(viewModel)
        withTimeout(WAIT_MS) { viewModel.state.first { it.history.isNotEmpty() } }

        assertEquals(listOf(statement), viewModel.state.value.history)
    }

    private fun console(sent: MutableList<String>): ConsoleViewModel {
        val api = mockCloudflareApi {
            post(RAW) { request ->
                sent += request.bodyText.orEmpty()
                DemoReply.ok(buildJsonArray { add(buildJsonObject { put("success", true) }) })
            }
        }
        val file = folder.newFile("history-${System.nanoTime()}.preferences_pb")
        val store = QueryHistoryStore(PreferenceDataStoreFactory.create(produceFile = { file }))
        return ConsoleViewModel(session(), SqlRepository(api), store, "db-1")
    }

    private suspend fun awaitIdle(viewModel: ConsoleViewModel) {
        withTimeout(WAIT_MS) { viewModel.state.first { !it.isRunning } }
    }

    private fun session() = FakeSessionRepository(
        SessionState.SignedIn(listOf(CloudflareAccount(id = "acc-1", name = "acc-1")), selectedAccountId = "acc-1", isDemo = false),
    )
}
