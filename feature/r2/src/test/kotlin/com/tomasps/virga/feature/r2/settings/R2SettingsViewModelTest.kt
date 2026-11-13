package com.tomasps.virga.feature.r2.settings

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.InMemoryStore
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.testing.FakeSessionRepository
import com.tomasps.virga.feature.r2.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import io.ktor.http.HttpStatusCode
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L

class R2SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `turning on public access asks first and only sends the request after confirmation`() = runBlocking {
        // Arrange
        val managed = InMemoryStore(mapOf("enabled" to false))
        var putBodies = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/domains/managed") {
                DemoReply.ok(buildJsonObject { put("enabled", managed.get("enabled") == true) })
            }
            put("/accounts/{accountId}/r2/buckets/{bucketName}/domains/managed") { request ->
                putBodies++
                managed.put("enabled", request.bodyText.orEmpty().contains("true"))
                DemoReply.ok(buildJsonObject {})
            }
            get("/accounts/{accountId}/r2/buckets/{bucketName}/domains/custom") { DemoReply.ok(buildJsonObject {}) }
            get("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { DemoReply.ok(buildJsonObject {}) }
            get("/accounts/{accountId}/r2/buckets/{bucketName}/lock") { DemoReply.ok(buildJsonObject {}) }
            get("/accounts/{accountId}/r2/buckets/{bucketName}/lifecycle") { DemoReply.ok(buildJsonObject {}) }
        }
        val viewModel = R2SettingsViewModel(FakeSessionRepository(), R2Repository(api), "site")
        withTimeout(WAIT_MS) { viewModel.state.first { it.publicAccess is UiState.Content } }

        // Act
        viewModel.requestPublicAccess(enable = true)
        val beforeConfirm = viewModel.state.value.pendingPublicAccess
        viewModel.confirmPublicAccess()

        // Assert
        assertEquals(true, beforeConfirm)
        val after = withTimeout(WAIT_MS) {
            viewModel.state.first { it.publicAccess is UiState.Content && (it.publicAccess as UiState.Content).data.enabled }
        }
        assertEquals(1, putBodies)
        assertNull(after.pendingPublicAccess)
        assertNotNull(after.publicAccess)
    }

    @Test
    fun `adding a lock rule while the lock list failed to load sends nothing and wipes nothing`() = runBlocking {
        // Arrange
        var lockWrites = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/domains/managed") { DemoReply.ok(buildJsonObject { put("enabled", false) }) }
            get("/accounts/{accountId}/r2/buckets/{bucketName}/domains/custom") { DemoReply.ok(buildJsonObject {}) }
            get("/accounts/{accountId}/r2/buckets/{bucketName}/lock") { DemoReply.failure(HttpStatusCode.InternalServerError, 1000, "boom") }
            put("/accounts/{accountId}/r2/buckets/{bucketName}/lock") {
                lockWrites++
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = R2SettingsViewModel(FakeSessionRepository(), R2Repository(api), "site")
        withTimeout(WAIT_MS) { viewModel.state.first { it.lock is UiState.Failure } }

        // Act
        viewModel.openLockForm()
        viewModel.onLockIdChanged("keep-logs")
        viewModel.onLockIndefiniteChanged(true)
        viewModel.submitLockRule()

        // Assert
        assertEquals(0, lockWrites)
        assertEquals(UiText.Resource(R.string.r2_settings_reload_first), viewModel.state.value.message)
    }

    @Test
    fun `removing a lock rule while the lock list failed to load sends nothing`() = runBlocking {
        // Arrange
        var lockWrites = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/domains/managed") { DemoReply.ok(buildJsonObject { put("enabled", false) }) }
            get("/accounts/{accountId}/r2/buckets/{bucketName}/domains/custom") { DemoReply.ok(buildJsonObject {}) }
            get("/accounts/{accountId}/r2/buckets/{bucketName}/lock") { DemoReply.failure(HttpStatusCode.InternalServerError, 1000, "boom") }
            put("/accounts/{accountId}/r2/buckets/{bucketName}/lock") {
                lockWrites++
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = R2SettingsViewModel(FakeSessionRepository(), R2Repository(api), "site")
        withTimeout(WAIT_MS) { viewModel.state.first { it.lock is UiState.Failure } }

        // Act
        viewModel.requestRemoveLockRule("existing-rule")
        viewModel.confirmRemoveLockRule()

        // Assert
        assertEquals(0, lockWrites)
        assertNull(viewModel.state.value.lockRuleToRemove)
    }
}
