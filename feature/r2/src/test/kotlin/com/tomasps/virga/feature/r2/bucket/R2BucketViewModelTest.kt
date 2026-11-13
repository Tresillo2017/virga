package com.tomasps.virga.feature.r2.bucket

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.testing.FakeSessionRepository
import com.tomasps.virga.feature.r2.testing.MainDispatcherRule
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNotNull

private const val WAIT_MS = 5_000L
private const val BUCKET = "site"

class R2BucketViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `a bucket that still holds objects is refused with a message saying it must be emptied`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}") { DemoReply.ok(bucketJson()) }
            delete("/accounts/{accountId}/r2/buckets/{bucketName}") {
                DemoReply.failure(HttpStatusCode.Conflict, 10008, "The bucket is not empty")
            }
        }
        val viewModel = R2BucketViewModel(FakeSessionRepository(), R2Repository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.bucket is UiState.Content } }

        // Act
        viewModel.requestDelete()
        viewModel.confirmDelete()
        val after = withTimeout(WAIT_MS) { viewModel.state.first { !it.isDeleting && it.deleteError != null } }

        // Assert
        val title = (requireNotNull(after.deleteError).title as UiText.Resource).id
        assertEquals(R.string.r2_delete_bucket_refused_title, title)
        assertTrue(!after.deleted)
    }

    @Test
    fun `a successful delete marks the bucket deleted so the screen can leave`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}") { DemoReply.ok(bucketJson()) }
            delete("/accounts/{accountId}/r2/buckets/{bucketName}") { DemoReply.empty() }
        }
        val viewModel = R2BucketViewModel(FakeSessionRepository(), R2Repository(api), BUCKET)
        withTimeout(WAIT_MS) { viewModel.state.first { it.bucket is UiState.Content } }

        // Act
        viewModel.requestDelete()
        viewModel.confirmDelete()
        val after = withTimeout(WAIT_MS) { viewModel.state.first { it.deleted } }

        // Assert
        assertTrue(after.deleted)
        assertTrue(after.deleteError == null)
    }

    private fun bucketJson() = buildJsonObject {
        put("name", BUCKET)
        put("location", "enam")
        put("storage_class", "Standard")
    }

    @Test
    fun `after the account changes the bucket cannot be deleted from the screen that was opened under the old account`() = runBlocking {
        // Arrange
        val session = FakeSessionRepository()
        var deletes = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}") { DemoReply.ok(buildJsonObject { put("name", "site") }) }
            delete("/accounts/{accountId}/r2/buckets/{bucketName}") {
                deletes++
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = R2BucketViewModel(session, R2Repository(api), "site")
        withTimeout(WAIT_MS) { viewModel.state.first { it.bucket is UiState.Content } }
        session.select("acc-2")

        // Act
        viewModel.requestDelete()
        viewModel.confirmDelete()

        // Assert
        assertEquals(0, deletes)
        assertNotNull(viewModel.state.value.deleteError)
    }
}
