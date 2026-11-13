package com.tomasps.virga.feature.domains.zones.list

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import io.ktor.http.HttpStatusCode
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.domains.testing.MainDispatcherRule
import com.tomasps.virga.feature.domains.zones.ZoneRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ZoneListViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val waitMs = 10_000L

    private class FakeSession(accountId: String) : SessionRepository {
        override val state: StateFlow<SessionState> = MutableStateFlow(
            SessionState.SignedIn(listOf(CloudflareAccount(accountId)), selectedAccountId = accountId, isDemo = false),
        )

        override suspend fun restore() = Unit
        override suspend fun signInWithToken(token: String): ApiResult<Unit> = throw UnsupportedOperationException()
        override suspend fun enterDemo(): ApiResult<Unit> = throw UnsupportedOperationException()
        override suspend fun selectAccount(accountId: String) = Unit
        override suspend fun signOut() = Unit
    }

    private fun zoneJson() = buildJsonObject {
        put("id", "z9")
        put("name", "example.org")
        put("status", "pending")
    }

    @Test
    fun `a second add while the first is in flight does not create the zone twice`() = runBlocking {
        // Arrange
        val posts = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val api = mockCloudflareApi {
            get("/zones") {
                DemoReply.ok(JsonArray(emptyList()), buildJsonObject { put("page", 1); put("per_page", 50); put("count", 0); put("total_count", 0) })
            }
            post("/zones") { request ->
                posts += request.bodyText.orEmpty()
                gate.await()
                DemoReply.ok(zoneJson())
            }
        }
        val vm = ZoneListViewModel(FakeSession("acc1"), ZoneRepository(api))
        withTimeout(waitMs) { vm.state.first { it.zones is UiState.Content } }

        // Act
        vm.addZone("example.org")
        vm.addZone("example.org")
        assertTrue(vm.state.value.creating)
        gate.complete(Unit)
        withTimeout(waitMs) { vm.state.first { !it.creating } }

        // Assert
        assertEquals(1, posts.size)
    }

    @Test
    fun `a name that is not a registrable domain is refused without a request`() = runBlocking {
        // Arrange
        val posts = mutableListOf<String>()
        val api = mockCloudflareApi {
            get("/zones") {
                DemoReply.ok(JsonArray(emptyList()), buildJsonObject { put("page", 1); put("per_page", 50); put("count", 0); put("total_count", 0) })
            }
            post("/zones") { request ->
                posts += request.bodyText.orEmpty()
                DemoReply.ok(zoneJson())
            }
        }
        val vm = ZoneListViewModel(FakeSession("acc1"), ZoneRepository(api))
        withTimeout(waitMs) { vm.state.first { it.zones is UiState.Content } }

        // Act
        vm.addZone("not a domain")

        // Assert
        assertEquals(0, posts.size)
        assertFalse(vm.state.value.creating)
        assertTrue(vm.state.value.addError != null)
        assertEquals(null, vm.state.value.message)
    }

    @Test
    fun `a failed add keeps the form open and shows the reason inside it`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/zones") {
                DemoReply.ok(JsonArray(emptyList()), buildJsonObject { put("page", 1); put("per_page", 50); put("count", 0); put("total_count", 0) })
            }
            post("/zones") {
                DemoReply.failure(HttpStatusCode.BadRequest, 1061, "Site already exists")
            }
        }
        val vm = ZoneListViewModel(FakeSession("acc1"), ZoneRepository(api))
        withTimeout(waitMs) { vm.state.first { it.zones is UiState.Content } }
        vm.showAddZone(true)

        // Act
        vm.addZone("example.org")
        withTimeout(waitMs) { vm.state.first { !it.creating } }

        // Assert
        assertTrue(vm.state.value.addingZone)
        assertTrue(vm.state.value.addError != null)
        assertEquals(null, vm.state.value.createdZoneId)
    }

    @Test
    fun `a zone that is deleted elsewhere is gone from the list without leaving the screen`() = runBlocking {
        // Arrange
        val zonesOnAccount = mutableListOf(zoneJson())
        val api = mockCloudflareApi {
            get("/zones") {
                DemoReply.ok(JsonArray(zonesOnAccount.toList()), buildJsonObject { put("page", 1); put("per_page", 50); put("count", zonesOnAccount.size); put("total_count", zonesOnAccount.size) })
            }
            delete("/zones/{zoneId}") { request ->
                zonesOnAccount.removeAll { (it["id"] as? JsonPrimitive)?.content == request.pathParams["zoneId"] }
                DemoReply.ok(buildJsonObject { put("id", request.pathParams["zoneId"].orEmpty()) })
            }
        }
        val zones = ZoneRepository(api)
        val vm = ZoneListViewModel(FakeSession("acc1"), zones)
        withTimeout(waitMs) { vm.state.first { (it.zones as? UiState.Content)?.data?.size == 1 } }

        // Act
        zones.delete("z9")

        // Assert
        withTimeout(waitMs) { vm.state.first { (it.zones as? UiState.Content)?.data?.isEmpty() == true } }
        Unit
    }
}
