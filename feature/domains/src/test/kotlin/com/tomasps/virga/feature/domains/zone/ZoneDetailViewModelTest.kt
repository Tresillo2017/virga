package com.tomasps.virga.feature.domains.zone

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.domains.analytics.ZoneTrafficRepository
import com.tomasps.virga.feature.domains.testing.MainDispatcherRule
import com.tomasps.virga.feature.domains.zones.PurgeRequest
import com.tomasps.virga.feature.domains.zones.ZoneConnectionsRepository
import com.tomasps.virga.feature.domains.zones.ZoneRepository
import com.tomasps.virga.feature.domains.zones.ZoneSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ZoneDetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val waitMs = 10_000L

    private fun viewModel(
        paused: Boolean = false,
        purgeCalls: MutableList<String> = mutableListOf(),
        deletes: MutableList<String> = mutableListOf(),
        deleteFails: Boolean = false,
    ): ZoneDetailViewModel {
        val api = mockCloudflareApi {
            get("/zones/{zoneId}") {
                DemoReply.ok(buildJsonObject {
                    put("id", "z1")
                    put("name", "example.com")
                    put("paused", paused)
                    put("account", buildJsonObject { put("id", "acc-1") })
                })
            }
            patch("/zones/{zoneId}") {
                DemoReply.ok(buildJsonObject { put("id", "z1"); put("name", "example.com"); put("paused", true) })
            }
            delete("/zones/{zoneId}") { request ->
                deletes += request.pathParams["zoneId"].orEmpty()
                if (deleteFails) {
                    DemoReply.failure(io.ktor.http.HttpStatusCode.Forbidden, 9109, "Forbidden")
                } else {
                    DemoReply.ok(buildJsonObject { put("id", "z1") })
                }
            }
            post("/zones/{zoneId}/purge_cache") { request ->
                purgeCalls += request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("id", "z1") })
            }
            get("/zones/{zoneId}/settings/development_mode") {
                DemoReply.ok(buildJsonObject { put("id", "development_mode"); put("value", "off"); put("editable", true); put("time_remaining", false) })
            }
            get("/zones/{zoneId}/workers/routes") { DemoReply.ok(JsonArray(emptyList())) }
            get("/accounts/{accountId}/registrar/registrations/{domain}") {
                DemoReply.failure(io.ktor.http.HttpStatusCode.NotFound, 7003, "not found")
            }
            post("/graphql") {
                DemoReply.graphQl(buildJsonObject { put("viewer", buildJsonObject { put("zones", JsonArray(emptyList())) }) })
            }
        }
        return ZoneDetailViewModel(
            zoneId = "z1",
            zones = ZoneRepository(api),
            settings = ZoneSettingsRepository(api),
            connections = ZoneConnectionsRepository(api),
            traffic = ZoneTrafficRepository(api.graphQl),
        )
    }

    @Test
    fun `loads the zone and reports no registrar when the domain is not registered here`() = runBlocking {
        // Arrange
        val vm = viewModel()

        // Act
        val state = withTimeout(waitMs) { vm.state.first { it.registrar !is UiState.Loading } }

        // Assert
        assertEquals("example.com", (state.zone as UiState.Content).data.name)
        assertEquals(UiState.Content(null), state.registrar)
    }

    @Test
    fun `an invalid purge selection sets a message and does not ask for confirmation`() = runBlocking {
        // Arrange
        val vm = viewModel()
        withTimeout(waitMs) { vm.state.first { it.zone is UiState.Content } }

        // Act
        vm.requestPurge(PurgeRequest.Files(listOf("no-scheme.example.com/a.css")))

        // Assert
        val state = vm.state.value
        assertNull(state.pendingAction)
        assertTrue(state.message != null)
    }

    @Test
    fun `a confirmed purge sends the request once`() = runBlocking {
        // Arrange
        val calls = mutableListOf<String>()
        val vm = viewModel(purgeCalls = calls)
        withTimeout(waitMs) { vm.state.first { it.zone is UiState.Content } }

        // Act
        vm.requestPurge(PurgeRequest.Everything)
        vm.confirmAction()
        withTimeout(waitMs) { vm.state.first { !it.busy } }

        // Assert
        assertEquals(1, calls.size)
        assertTrue(calls.single().contains("purge_everything"))
        assertTrue(!vm.state.value.busy)
    }

    @Test
    fun `pausing waits for confirmation before the PATCH is sent`() = runBlocking {
        // Arrange
        val vm = viewModel()
        withTimeout(waitMs) { vm.state.first { it.zone is UiState.Content } }

        // Act
        vm.requestAction(ZoneAction.SetPaused(true))
        val pending = vm.state.value.pendingAction

        // Assert
        assertEquals(ZoneAction.SetPaused(true), pending)
    }

    @Test
    fun `asking to delete the zone sends nothing until the typed name is confirmed`() = runBlocking {
        // Arrange
        val deletes = mutableListOf<String>()
        val vm = viewModel(deletes = deletes)
        withTimeout(waitMs) { vm.state.first { it.zone is UiState.Content } }

        // Act
        vm.requestAction(ZoneAction.Delete)

        // Assert
        assertEquals(ZoneAction.Delete, vm.state.value.pendingAction)
        assertTrue(deletes.isEmpty())
    }

    @Test
    fun `dismissing the delete request sends nothing and keeps the zone`() = runBlocking {
        // Arrange
        val deletes = mutableListOf<String>()
        val vm = viewModel(deletes = deletes)
        withTimeout(waitMs) { vm.state.first { it.zone is UiState.Content } }
        vm.requestAction(ZoneAction.Delete)

        // Act
        vm.dismissAction()

        // Assert
        assertNull(vm.state.value.pendingAction)
        assertTrue(deletes.isEmpty())
        assertTrue(vm.state.value.zone is UiState.Content)
    }

    @Test
    fun `a confirmed delete sends one DELETE for the zone and marks it deleted`() = runBlocking {
        // Arrange
        val deletes = mutableListOf<String>()
        val vm = viewModel(deletes = deletes)
        withTimeout(waitMs) { vm.state.first { it.zone is UiState.Content } }
        vm.requestAction(ZoneAction.Delete)

        // Act
        vm.confirmAction()
        withTimeout(waitMs) { vm.state.first { it.deleted } }

        // Assert
        assertEquals(listOf("z1"), deletes)
        assertTrue(!vm.state.value.busy)
    }

    @Test
    fun `a refused delete keeps the zone on screen and says why`() = runBlocking {
        // Arrange
        val deletes = mutableListOf<String>()
        val vm = viewModel(deletes = deletes, deleteFails = true)
        withTimeout(waitMs) { vm.state.first { it.zone is UiState.Content } }
        vm.requestAction(ZoneAction.Delete)

        // Act
        vm.confirmAction()
        withTimeout(waitMs) { vm.state.first { !it.busy && it.message != null } }

        // Assert
        assertEquals(1, deletes.size)
        assertTrue(!vm.state.value.deleted)
        assertTrue(vm.state.value.zone is UiState.Content)
    }
}
