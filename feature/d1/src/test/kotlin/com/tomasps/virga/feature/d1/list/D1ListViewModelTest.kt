package com.tomasps.virga.feature.d1.list

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.D1Database
import com.tomasps.virga.feature.d1.data.D1Limits
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.list.RecentlyDeletedDatabase
import com.tomasps.virga.feature.d1.testing.FakeSessionRepository
import com.tomasps.virga.feature.d1.testing.InMemoryRecentlyDeletedLog
import io.ktor.http.HttpStatusCode
import com.tomasps.virga.feature.d1.testing.MainDispatcherRule
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
private const val NOW_MILLIS = 1_800_000_000_000L
private const val LIST = "/accounts/{accountId}/d1/database"
private const val ONE = "/accounts/{accountId}/d1/database/{databaseId}"
private const val UNDELETE = "/accounts/{accountId}/d1/database/{databaseId}/undelete"

class D1ListViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `the first page of the selected account is loaded as content`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi { get(LIST) { DemoReply.ok(databases("orders", "ledger")) } }

        // Act
        val content = awaitContent(viewModel(api))

        // Assert
        assertEquals(listOf("orders", "ledger"), content.items.map { it.name })
        assertEquals(LoadMoreState.HIDDEN, content.loadMore)
    }

    @Test
    fun `a full page keeps the footer available, and the next page appends`() = runBlocking {
        // Arrange
        val pageSize = D1Limits.LIST_PAGE_SIZE
        val total = pageSize + 1
        val api = mockCloudflareApi {
            get(LIST) { request ->
                if (request.query["page"] == "1") {
                    DemoReply.ok(databases(*Array(pageSize) { "db-$it" }), resultInfo = pageInfo(page = 1, total = total))
                } else {
                    DemoReply.ok(databases("last"), resultInfo = pageInfo(page = 2, total = total))
                }
            }
        }
        val viewModel = viewModel(api)
        val first = awaitContent(viewModel)
        assertEquals(LoadMoreState.AVAILABLE, first.loadMore)

        // Act
        viewModel.loadMore()
        val content = awaitContent(viewModel) { it.items.size == total }

        // Assert
        assertEquals("last", content.items.last().name)
        assertEquals(LoadMoreState.HIDDEN, content.loadMore)
    }

    @Test
    fun `an empty account is content with no items, not an error`() = runBlocking {
        val api = mockCloudflareApi { get(LIST) { DemoReply.ok(databases()) } }

        val content = awaitContent(viewModel(api))

        assertTrue(content.items.isEmpty())
    }

    @Test
    fun `a blank name is refused before any request is made`() = runBlocking {
        // Arrange
        var creates = 0
        val api = mockCloudflareApi {
            get(LIST) { DemoReply.ok(databases()) }
            post(LIST) {
                creates++
                DemoReply.ok(buildJsonObject { put("name", "x") })
            }
        }
        val viewModel = viewModel(api)
        awaitContent(viewModel)

        // Act
        viewModel.openCreate()
        viewModel.submitCreate()

        // Assert
        assertEquals(0, creates)
        assertTrue(viewModel.state.value.create?.nameError == true)
    }

    @Test
    fun `a created database closes the dialog and the list reloads`() = runBlocking {
        // Arrange
        var listed = 0
        val api = mockCloudflareApi {
            get(LIST) {
                listed++
                DemoReply.ok(if (listed == 1) databases() else databases("ledger"))
            }
            post(LIST) { request ->
                assertTrue(request.bodyText.orEmpty().contains("\"name\":\"ledger\""))
                DemoReply.ok(buildJsonObject { put("uuid", "d-9"); put("name", "ledger") })
            }
        }
        val viewModel = viewModel(api)
        awaitContent(viewModel)

        // Act
        viewModel.openCreate()
        viewModel.onNameChanged("ledger")
        viewModel.submitCreate()
        val content = awaitContent(viewModel) { it.items.isNotEmpty() }

        // Assert
        assertNull(viewModel.state.value.create)
        assertEquals("ledger", content.items.single().name)
    }

    @Test
    fun `switching accounts reloads the list for the new account`() = runBlocking {
        // Arrange
        val session = FakeSessionRepository(signedIn("acc-1"))
        val seen = mutableListOf<String>()
        val api = mockCloudflareApi {
            get(LIST) { request ->
                val account = request.pathParams["accountId"].orEmpty()
                seen += account
                DemoReply.ok(databases(account))
            }
        }
        val viewModel = D1ListViewModel(session, DatabasesRepository(api), InMemoryRecentlyDeletedLog())
        awaitContent(viewModel)

        // Act
        session.emit(signedIn("acc-2"))
        val content = awaitContent(viewModel) { it.items.singleOrNull()?.name == "acc-2" }

        // Assert
        assertEquals(listOf("acc-1", "acc-2"), seen)
        assertEquals("acc-2", content.items.single().name)
    }

    private fun viewModel(
        api: CloudflareApi,
        log: InMemoryRecentlyDeletedLog = InMemoryRecentlyDeletedLog(),
    ) = D1ListViewModel(FakeSessionRepository(signedIn("acc-1")), DatabasesRepository(api), log, nowMillis = { NOW_MILLIS })

    @Test
    fun `a confirmed delete is remembered and can be restored, which forgets it`() = runBlocking<Unit> {
        // Arrange
        val log = InMemoryRecentlyDeletedLog()
        var undeletes = 0
        val api = mockCloudflareApi {
            get(LIST) { DemoReply.ok(databases("orders")) }
            delete(ONE) { DemoReply.empty() }
            post(UNDELETE) {
                undeletes++
                DemoReply.ok(buildJsonObject { put("uuid", "uuid-orders"); put("name", "orders") })
            }
        }
        val viewModel = viewModel(api, log)
        awaitContent(viewModel)
        viewModel.requestDelete(D1Database(uuid = "uuid-orders", name = "orders"))

        // Act: delete, then restore from the recently deleted list
        viewModel.confirmDelete()
        val entry = withTimeout(WAIT_MS) { viewModel.state.first { it.recentlyDeleted.isNotEmpty() } }.recentlyDeleted.single()
        viewModel.restore(entry)
        withTimeout(WAIT_MS) { viewModel.state.first { it.restoringUuid == null && it.recentlyDeleted.isEmpty() } }

        // Assert
        assertEquals("orders", entry.name)
        assertEquals(1, undeletes)
        assertTrue(log.current("acc-1").isEmpty())
    }

    @Test
    fun `an entry past the undelete window is not offered for restore`() = runBlocking<Unit> {
        // Arrange: deleted just outside the seven-day window
        val expired = RecentlyDeletedDatabase(uuid = "old", name = "old", deletedAtMillis = NOW_MILLIS - D1Limits.UNDELETE_WINDOW_MILLIS)
        val viewModel = viewModel(
            mockCloudflareApi { get(LIST) { DemoReply.ok(databases()) } },
            InMemoryRecentlyDeletedLog(mapOf("acc-1" to listOf(expired))),
        )

        // Act
        awaitContent(viewModel)

        // Assert
        assertTrue(viewModel.state.value.recentlyDeleted.isEmpty())
    }

    @Test
    fun `a refused restore keeps the entry and shows the error`() = runBlocking<Unit> {
        // Arrange
        val entry = RecentlyDeletedDatabase(uuid = "uuid-orders", name = "orders", deletedAtMillis = NOW_MILLIS)
        val api = mockCloudflareApi {
            get(LIST) { DemoReply.ok(databases()) }
            post(UNDELETE) { DemoReply.failure(HttpStatusCode.BadRequest, 1, "name taken") }
        }
        val viewModel = viewModel(api, InMemoryRecentlyDeletedLog(mapOf("acc-1" to listOf(entry))))
        awaitContent(viewModel)

        // Act
        viewModel.restore(entry)
        withTimeout(WAIT_MS) { viewModel.state.first { it.actionError != null } }

        // Assert
        assertEquals(listOf(entry), viewModel.state.value.recentlyDeleted)
        assertNull(viewModel.state.value.restoringUuid)
    }

    private suspend fun awaitContent(
        viewModel: D1ListViewModel,
        predicate: (DatabaseListContent) -> Boolean = { true },
    ): DatabaseListContent = withTimeout(WAIT_MS) {
        viewModel.state.first { state ->
            val content = (state.databases as? UiState.Content)?.data
            content != null && predicate(content)
        }
        (viewModel.state.value.databases as UiState.Content).data
    }

    private fun signedIn(accountId: String) = SessionState.SignedIn(
        accounts = listOf(CloudflareAccount(id = accountId, name = accountId)),
        selectedAccountId = accountId,
        isDemo = false,
    )

    private fun databases(vararg names: String): JsonArray = buildJsonArray {
        names.forEach { name -> add(buildJsonObject { put("uuid", "uuid-$name"); put("name", name) }) }
    }

    private fun pageInfo(page: Int, total: Int) = buildJsonObject {
        put("page", page)
        put("per_page", D1Limits.LIST_PAGE_SIZE)
        put("total_count", total)
    }
}
