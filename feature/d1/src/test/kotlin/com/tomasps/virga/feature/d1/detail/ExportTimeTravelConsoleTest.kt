package com.tomasps.virga.feature.d1.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.ExportOptions
import com.tomasps.virga.feature.d1.data.ExportRepository
import com.tomasps.virga.feature.d1.data.SqlRepository
import com.tomasps.virga.feature.d1.data.TimeTravelRepository
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.detail.console.ConsoleViewModel
import com.tomasps.virga.feature.d1.detail.export.ExportPhase
import com.tomasps.virga.feature.d1.detail.export.ExportUiState
import com.tomasps.virga.feature.d1.detail.export.ExportViewModel
import com.tomasps.virga.feature.d1.detail.timetravel.RestoreTarget
import com.tomasps.virga.feature.d1.detail.timetravel.TimeTravelUiState
import com.tomasps.virga.feature.d1.detail.timetravel.TimeTravelViewModel
import com.tomasps.virga.feature.d1.sql.QueryHistoryStore
import com.tomasps.virga.feature.d1.testing.FakeSessionRepository
import com.tomasps.virga.feature.d1.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val WAIT_MS = 5_000L
private const val DB = "/accounts/{accountId}/d1/database/{databaseId}"
private const val EXPORT = "$DB/export"
private const val RESTORE = "$DB/time_travel/restore"
private const val RAW = "$DB/raw"

class ExportTimeTravelConsoleTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a completed export exposes its link to the download step but hides it in toString`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(EXPORT) { request ->
                if (request.bodyText.orEmpty().contains("current_bookmark")) {
                    DemoReply.ok(buildJsonObject {
                        put("status", "complete")
                        put("at_bookmark", "bm-1")
                        putJsonObject("result") {
                            put("filename", "orders.sql")
                            put("signed_url", "https://example.invalid/orders.sql?sig=secret")
                        }
                    })
                } else {
                    DemoReply.ok(buildJsonObject { put("at_bookmark", "bm-1") })
                }
            }
        }
        val viewModel = ExportViewModel(session(), ExportRepository(api, pause = {}), "db-1")
        awaitExport(viewModel) { it.phase is ExportPhase.Idle }

        viewModel.start()
        val ready = awaitExport(viewModel) { it.phase is ExportPhase.Ready }.phase as ExportPhase.Ready

        assertEquals("orders.sql", ready.download.filename)
        assertTrue(ready.download.signedUrl.startsWith("https://"))
        assertFalse(ready.toString().contains("secret"))
        assertFalse(ready.download.toString().contains("sig="))
    }

    @Test
    fun `a download link that is not https is refused`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(EXPORT) { DemoReply.ok(buildJsonObject { put("status", "complete"); put("at_bookmark", "b"); putJsonObject("result") { put("signed_url", "http://insecure.invalid/x.sql") } }) }
        }
        val viewModel = ExportViewModel(session(), ExportRepository(api, pause = {}), "db-1")
        awaitExport(viewModel) { it.phase is ExportPhase.Idle }

        viewModel.start()

        assertTrue(awaitExport(viewModel) { it.phase is ExportPhase.Failed }.phase is ExportPhase.Failed)
    }

    @Test
    fun `a timestamp that is not ISO 8601 is refused before any request`() = runBlocking<Unit> {
        var restores = 0
        val viewModel = timeTravel(mockCloudflareApi {
            get(DB) { DemoReply.ok(buildJsonObject { put("name", "orders") }) }
            get("$DB/time_travel/bookmark") { DemoReply.ok(buildJsonObject { put("bookmark", "bm-now") }) }
            post(RESTORE) { restores++; DemoReply.ok(buildJsonObject { put("bookmark", "bm-x") }) }
        })
        awaitTravel(viewModel) { it.currentBookmark is UiState.Content }

        viewModel.onLookupChanged("yesterday")
        viewModel.requestRestoreToTimestamp()

        assertNull(viewModel.state.value.pendingRestore)
        assertNotNull(viewModel.state.value.lookupError)
        assertEquals(0, restores)
    }

    @Test
    fun `a restore is staged until confirmed and then calls the restore path with the timestamp`() = runBlocking<Unit> {
        var seenTimestamp: String? = null
        val viewModel = timeTravel(mockCloudflareApi {
            get(DB) { DemoReply.ok(buildJsonObject { put("name", "orders") }) }
            get("$DB/time_travel/bookmark") { DemoReply.ok(buildJsonObject { put("bookmark", "bm-now") }) }
            post(RESTORE) { request ->
                seenTimestamp = request.query["timestamp"]
                DemoReply.ok(buildJsonObject { put("bookmark", "bm-x"); put("message", "restored") })
            }
        })
        awaitTravel(viewModel) { it.currentBookmark is UiState.Content }

        viewModel.onLookupChanged("2026-11-10T08:00:00Z")
        viewModel.requestRestoreToTimestamp()
        assertEquals(RestoreTarget.Timestamp("2026-11-10T08:00:00Z"), viewModel.state.value.pendingRestore)
        assertNull(seenTimestamp)

        viewModel.confirmRestore()
        awaitTravel(viewModel) { it.restoredMessage != null }

        assertEquals("2026-11-10T08:00:00Z", seenTimestamp)
    }

    @Test
    fun `a destructive console statement waits for confirmation before it runs`() = runBlocking<Unit> {
        var executed = 0
        val api = mockCloudflareApi {
            post(RAW) { request ->
                if (request.bodyText.orEmpty().contains("DELETE")) executed++
                DemoReply.ok(buildJsonObject { })
            }
        }
        val folderFile = folder.newFile("history.preferences_pb")
        val store = QueryHistoryStore(PreferenceDataStoreFactory.create(produceFile = { folderFile }))
        val viewModel = ConsoleViewModel(session(), SqlRepository(api), store, "db-1")

        viewModel.onSqlChanged("DELETE FROM orders")
        viewModel.run()
        assertNotNull(viewModel.state.value.pendingRun)
        assertEquals(0, executed)

        viewModel.cancelRun()
        assertNull(viewModel.state.value.pendingRun)
        assertEquals(0, executed)
    }

    private fun session() = FakeSessionRepository(
        SessionState.SignedIn(listOf(CloudflareAccount(id = "acc-1", name = "acc-1")), selectedAccountId = "acc-1", isDemo = false),
    )

    private fun timeTravel(api: com.tomasps.virga.core.network.CloudflareApi) = TimeTravelViewModel(
        session = session(),
        databases = DatabasesRepository(api),
        timeTravel = TimeTravelRepository(api),
        databaseId = "db-1",
    )

    private suspend fun awaitExport(viewModel: ExportViewModel, predicate: (ExportUiState) -> Boolean): ExportUiState =
        withTimeout(WAIT_MS) { viewModel.state.first(predicate) }

    private suspend fun awaitTravel(viewModel: TimeTravelViewModel, predicate: (TimeTravelUiState) -> Boolean): TimeTravelUiState =
        withTimeout(WAIT_MS) { viewModel.state.first(predicate) }
}
