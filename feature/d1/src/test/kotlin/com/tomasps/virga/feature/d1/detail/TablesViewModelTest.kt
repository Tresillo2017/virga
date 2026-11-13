package com.tomasps.virga.feature.d1.detail

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.SqlRepository
import com.tomasps.virga.feature.d1.data.TableCatalogRepository
import com.tomasps.virga.feature.d1.data.TableRowsRepository
import com.tomasps.virga.feature.d1.detail.tables.TablesViewModel
import com.tomasps.virga.feature.d1.detail.tables.WriteKind
import com.tomasps.virga.feature.d1.testing.FakeSessionRepository
import com.tomasps.virga.feature.d1.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val SETTLE_MS = 200L
private const val FIRST_PAGE_ROWS = 50
private const val RAW = "/accounts/{accountId}/d1/database/{databaseId}/raw"

class TablesViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `an insert is shown as a parameterised statement and runs only after confirmation`() = runBlocking<Unit> {
        // Arrange
        var executed = 0
        val api = schemaApi(onWrite = { executed++ })
        val viewModel = viewModel(api)
        awaitTables(viewModel)
        viewModel.openTable("orders")
        awaitState(viewModel) { it.rows is UiState.Content && it.schema is UiState.Content }

        // Act
        viewModel.beginInsert()
        viewModel.onCellText("id", "7")
        viewModel.onCellText("total", "900")
        viewModel.reviewEditor()

        // Assert: nothing has run yet, and the pending statement is exact
        val pending = viewModel.state.value.pendingWrite
        assertNotNull(pending)
        assertEquals(WriteKind.INSERT, pending!!.kind)
        assertEquals("INSERT INTO \"orders\" (\"id\", \"total\") VALUES (?, ?)", pending.statement.sql)
        assertEquals(listOf("7", "900"), pending.statement.params)
        assertEquals(0, executed)

        viewModel.confirmWrite()
        awaitState(viewModel) { it.pendingWrite == null && !it.isWriting }
        assertEquals(1, executed)
    }

    @Test
    fun `a table without a primary key cannot be edited or deleted from the grid`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/d1/database/{databaseId}/raw") { DemoReply.failure(io.ktor.http.HttpStatusCode.NotFound, 1, "x") }
            post(RAW) { request ->
                val body = request.bodyText.orEmpty()
                when {
                    body.contains("pragma_table_info") -> columnsReply(pk = false)
                    body.contains("pragma_index_list") || body.contains("pragma_foreign_key_list") -> emptyReply()
                    body.contains("sqlite_master") -> tablesReply()
                    else -> rowsReply()
                }
            }
        }
        val viewModel = viewModel(api)
        awaitTables(viewModel)
        viewModel.openTable("audit")
        awaitState(viewModel) { it.schema is UiState.Content && it.rows is UiState.Content }

        viewModel.beginInsert()
        assertNull(viewModel.state.value.editor)

        viewModel.requestDelete(0)
        assertNull(viewModel.state.value.pendingWrite)
        assertNotNull(viewModel.state.value.actionError)
    }

    @Test
    fun `a second confirm tap runs the write once`() = runBlocking<Unit> {
        // Arrange
        var executed = 0
        val viewModel = viewModel(schemaApi(onWrite = { executed++ }))
        awaitTables(viewModel)
        viewModel.openTable("orders")
        awaitState(viewModel) { it.rows is UiState.Content && it.schema is UiState.Content }
        viewModel.beginInsert()
        viewModel.onCellText("id", "7")
        viewModel.reviewEditor()

        // Act: two confirms in a row, as a double tap would send
        viewModel.confirmWrite()
        viewModel.confirmWrite()
        awaitState(viewModel) { !it.isWriting && it.pendingWrite == null }

        // Assert
        assertEquals(1, executed)
    }

    @Test
    fun `an edit writes only the columns that changed, keyed by the primary key`() = runBlocking<Unit> {
        // Arrange
        val viewModel = viewModel(schemaApi(onWrite = {}))
        awaitTables(viewModel)
        viewModel.openTable("orders")
        awaitState(viewModel) { it.rows is UiState.Content && it.schema is UiState.Content }

        // Act: row is (id=1, total=500); only total changes
        viewModel.beginEdit(0)
        viewModel.onCellText("total", "900")
        viewModel.reviewEditor()

        // Assert
        val pending = viewModel.state.value.pendingWrite
        assertNotNull(pending)
        assertEquals(WriteKind.UPDATE, pending!!.kind)
        assertEquals("UPDATE \"orders\" SET \"total\" = ? WHERE \"id\" = ?", pending.statement.sql)
        assertEquals(listOf("900", "1"), pending.statement.params)
    }

    @Test
    fun `saving an edit that changed nothing closes the editor without a write`() = runBlocking<Unit> {
        // Arrange
        val viewModel = viewModel(schemaApi(onWrite = {}))
        awaitTables(viewModel)
        viewModel.openTable("orders")
        awaitState(viewModel) { it.rows is UiState.Content && it.schema is UiState.Content }

        // Act
        viewModel.beginEdit(0)
        viewModel.reviewEditor()

        // Assert
        assertNull(viewModel.state.value.editor)
        assertNull(viewModel.state.value.pendingWrite)
        assertNull(viewModel.state.value.actionError)
    }

    @Test
    fun `a row holding an array is read-only, because its text form is not the stored value`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post(RAW) { request ->
                val body = request.bodyText.orEmpty()
                when {
                    body.contains("pragma_table_info") -> columnsReply(pk = true)
                    body.contains("pragma_index_list") || body.contains("pragma_foreign_key_list") -> emptyReply()
                    body.contains("sqlite_master") -> tablesReply()
                    else -> rawEnvelope(listOf("id", "total"), listOf(listOf(JsonPrimitive(1), buildJsonArray { add(JsonPrimitive(2)) })))
                }
            }
        }
        val viewModel = viewModel(api)
        awaitTables(viewModel)
        viewModel.openTable("orders")
        awaitState(viewModel) { it.rows is UiState.Content && it.schema is UiState.Content }

        // Act
        viewModel.beginEdit(0)

        // Assert
        assertNull(viewModel.state.value.editor)
        assertNotNull(viewModel.state.value.actionError)
    }

    @Test
    fun `a page that arrives after the user switched tables is dropped, not appended to the new table`() = runBlocking<Unit> {
        // Arrange: the second page of "orders" is held until the test releases it
        val releaseSecondPage = CompletableDeferred<Unit>()
        val api = mockCloudflareApi {
            post(RAW) { request ->
                val body = request.bodyText.orEmpty()
                when {
                    body.contains("pragma_table_info") -> columnsReply(pk = true)
                    body.contains("pragma_index_list") || body.contains("pragma_foreign_key_list") -> emptyReply()
                    body.contains("sqlite_master") -> tablesReply()
                    body.contains("OFFSET 50") -> {
                        releaseSecondPage.await()
                        rawEnvelope(listOf("id", "total"), List(3) { listOf(JsonPrimitive(it + 100), JsonPrimitive(0)) })
                    }
                    body.contains("audit") -> rawEnvelope(listOf("id", "total"), listOf(listOf(JsonPrimitive(9), JsonPrimitive(9))))
                    else -> rawEnvelope(listOf("id", "total"), List(FIRST_PAGE_ROWS + 1) { listOf(JsonPrimitive(it), JsonPrimitive(0)) })
                }
            }
        }
        val viewModel = viewModel(api)
        awaitTables(viewModel)
        viewModel.openTable("orders")
        awaitState(viewModel) { (it.rows as? UiState.Content)?.data?.hasMore == true }
        viewModel.loadMoreRows()

        // Act: switch tables while the second page is still in flight, then let it arrive
        viewModel.openTable("audit")
        awaitState(viewModel) { (it.rows as? UiState.Content)?.data?.rows?.size == 1 }
        releaseSecondPage.complete(Unit)
        delay(SETTLE_MS)

        // Assert: the audit grid still holds only its own row
        val rows = (viewModel.state.value.rows as UiState.Content).data.rows
        assertEquals(listOf(JsonPrimitive(9)), rows.map { it.first() })
    }

    private fun schemaApi(onWrite: () -> Unit) = mockCloudflareApi {
        post(RAW) { request ->
            val body = request.bodyText.orEmpty()
            when {
                body.contains("pragma_table_info") -> columnsReply(pk = true)
                body.contains("pragma_index_list") || body.contains("pragma_foreign_key_list") -> emptyReply()
                body.contains("sqlite_master") -> tablesReply()
                body.startsWith("{\"sql\":\"INSERT") -> { onWrite(); emptyReply() }
                else -> rowsReply()
            }
        }
    }

    private fun viewModel(api: com.tomasps.virga.core.network.CloudflareApi) = TablesViewModel(
        session = FakeSessionRepository(
            SessionState.SignedIn(listOf(CloudflareAccount(id = "acc-1", name = "acc-1")), selectedAccountId = "acc-1", isDemo = false),
        ),
        databaseId = "db-1",
        catalog = TableCatalogRepository(SqlRepository(api)),
        rowsRepository = TableRowsRepository(SqlRepository(api)),
    )

    private suspend fun awaitTables(viewModel: TablesViewModel) = awaitState(viewModel) { it.tables is UiState.Content }

    private suspend fun awaitState(viewModel: TablesViewModel, predicate: (com.tomasps.virga.feature.d1.detail.tables.TablesUiState) -> Boolean) {
        withTimeout(WAIT_MS) { viewModel.state.first { predicate(it) } }
    }

    private fun tablesReply() = DemoReply.ok(buildJsonArray { add(buildJsonObject { put("name", "orders") }) })
        .let { rawEnvelope(listOf("name"), listOf(listOf(JsonPrimitive("orders")))) }

    private fun columnsReply(pk: Boolean) = rawEnvelope(
        listOf("name", "type", "notnull", "dflt_value", "pk"),
        listOf(
            listOf(JsonPrimitive("id"), JsonPrimitive("INTEGER"), JsonPrimitive(0), null.toJsonNull(), JsonPrimitive(if (pk) 1 else 0)),
            listOf(JsonPrimitive("total"), JsonPrimitive("INTEGER"), JsonPrimitive(0), null.toJsonNull(), JsonPrimitive(0)),
        ),
    )

    private fun rowsReply() = rawEnvelope(listOf("id", "total"), listOf(listOf(JsonPrimitive(1), JsonPrimitive(500))))

    private fun emptyReply() = rawEnvelope(emptyList(), emptyList())

    private fun Any?.toJsonNull() = kotlinx.serialization.json.JsonNull

    private fun rawEnvelope(columns: List<String>, rows: List<List<kotlinx.serialization.json.JsonElement>>): DemoReply =
        DemoReply.ok(buildJsonArray {
            add(buildJsonObject {
                putJsonObject("results") {
                    putJsonArray("columns") { columns.forEach { add(JsonPrimitive(it)) } }
                    putJsonArray("rows") { rows.forEach { row -> add(buildJsonArray { row.forEach { add(it) } }) } }
                }
                putJsonObject("meta") {}
            })
        })
}
