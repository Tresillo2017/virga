package com.tomasps.virga.feature.r2.objects

import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.api.R2_SINGLE_PUT_MAX_BYTES
import com.tomasps.virga.feature.r2.testing.FakeSessionRepository
import com.tomasps.virga.feature.r2.testing.MainDispatcherRule
import com.tomasps.virga.feature.r2.transfer.DocumentInfo
import com.tomasps.virga.feature.r2.transfer.R2DocumentStreams
import com.tomasps.virga.feature.r2.transfer.R2TransferStatus
import com.tomasps.virga.feature.r2.transfer.R2TransferCoordinator
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

private const val WAIT_MS = 5_000L
private const val BUCKET = "site"

/** Reports one picked document of a fixed size. Nothing in these tests reads its bytes. */
private class PickedDocument(private val size: Long) : R2DocumentStreams {
    override fun openForRead(uriString: String): InputStream = error("not read in this test")
    override fun openForWrite(uriString: String): OutputStream = error("not written in this test")
    override fun describe(uriString: String): DocumentInfo = DocumentInfo("picked.bin", size, "application/octet-stream")
    override fun delete(uriString: String): Boolean = false
}

/** A picked document whose bytes can be read, for the upload tests. */
private class PickedFile(private val size: Long, private val bytes: String) : R2DocumentStreams {
    override fun openForRead(uriString: String): InputStream = ByteArrayInputStream(bytes.encodeToByteArray())
    override fun openForWrite(uriString: String): OutputStream = error("not written in this test")
    override fun describe(uriString: String): DocumentInfo = DocumentInfo("picked.bin", size, "application/octet-stream")
    override fun delete(uriString: String): Boolean = false
}

class R2ObjectsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `search is scoped to the folder on screen and does not send a delimiter`() = runBlocking {
        // Arrange
        val listings = mutableListOf<Pair<String?, Boolean>>()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") { request ->
                listings += request.query["prefix"] to request.query.containsKey("delimiter")
                DemoReply.ok(buildJsonArray {}, resultInfo = buildJsonObject { put("is_truncated", false) })
            }
        }
        val viewModel = viewModelFor(api, PickedDocument(size = 1L))
        withTimeout(WAIT_MS) { viewModel.state.first { it.listing is UiState.Content } }

        // Act
        viewModel.openFolder("img/")
        withTimeout(WAIT_MS) { viewModel.state.first { it.folderPrefix == "img/" && it.listing is UiState.Content } }
        viewModel.onSearchChanged("hero")
        mainDispatcher.scheduler.advanceUntilIdle()
        withTimeout(WAIT_MS) { viewModel.state.first { it.search == "hero" && it.listing is UiState.Content } }

        // Assert
        assertEquals("img/hero" to false, listings.last())
    }

    @Test
    fun `a file over the single PUT limit is refused before anything is sent`() = runBlocking {
        // Arrange
        val oversize = R2_SINGLE_PUT_MAX_BYTES + 1
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") {
                DemoReply.ok(buildJsonArray {}, resultInfo = buildJsonObject { put("is_truncated", false) })
            }
        }
        val streams = PickedDocument(size = oversize)
        val viewModel = viewModelFor(api, streams)
        withTimeout(WAIT_MS) { viewModel.state.first { it.listing is UiState.Content } }

        // Act
        viewModel.onUploadPicked("content://picked/big.bin")

        // Assert
        val error = requireNotNull(withTimeout(WAIT_MS) { viewModel.state.first { it.actionError != null } }.actionError)
        assertEquals(R.string.r2_upload_too_large_title, (error.title as UiText.Resource).id)
        assertEquals(formatBytes(oversize), (error.message as UiText.Resource).args.single())
    }

    @Test
    fun `deleting several objects reports how many went and how many failed`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") {
                DemoReply.ok(buildJsonArray {}, resultInfo = buildJsonObject { put("is_truncated", false) })
            }
            delete("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { request ->
                if (request.pathParams["objectKey"] == "broken.txt") {
                    DemoReply.failure(HttpStatusCode.InternalServerError, 500, "boom")
                } else {
                    DemoReply.empty()
                }
            }
        }
        val viewModel = viewModelFor(api, PickedDocument(size = 1L))
        withTimeout(WAIT_MS) { viewModel.state.first { it.listing is UiState.Content } }

        // Act
        viewModel.requestDelete(listOf("fine.txt", "broken.txt"))
        viewModel.confirmDelete()
        val after = withTimeout(WAIT_MS) { viewModel.state.first { !it.isDeleting && it.message != null } }

        // Assert
        assertEquals(R.string.r2_objects_delete_partial, (after.message as UiText.Resource).id)
        assertEquals(listOf(1, 1), (after.message as UiText.Resource).args.map { it as Int })
        assertTrue(after.pendingDelete == null)
    }

    @Test
    fun `a page requested before the folder changed is dropped and never appears in the new folder`() = runBlocking {
        // Arrange
        val gate = CompletableDeferred<Unit>()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") { request ->
                when {
                    request.query["cursor"] == "page-2" -> {
                        gate.await()
                        DemoReply.ok(buildJsonArray { add(objectJson("stale.txt")) }, resultInfo = truncatedAt(null))
                    }
                    request.query["prefix"] == "img/" -> DemoReply.ok(buildJsonArray { add(objectJson("img/a.png")) }, resultInfo = truncatedAt(null))
                    else -> DemoReply.ok(buildJsonArray { add(objectJson("first.txt")) }, resultInfo = truncatedAt("page-2"))
                }
            }
        }
        val viewModel = viewModelFor(api, PickedDocument(size = 1L))
        withTimeout(WAIT_MS) { viewModel.state.first { it.nextCursor == "page-2" } }
        viewModel.loadMore()

        // Act
        viewModel.openFolder("img/")
        withTimeout(WAIT_MS) { viewModel.state.first { it.folderPrefix == "img/" && it.listing is UiState.Content } }
        gate.complete(Unit)

        // Assert
        val listing = (viewModel.state.value.listing as UiState.Content).data
        assertEquals(listOf("img/a.png"), listing.objects.map { it.key })
        assertFalse(viewModel.state.value.isLoadingMore)
    }

    @Test
    fun `an upload over an existing key waits for confirmation and then replaces it`() = runBlocking {
        // Arrange
        var puts = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") { request ->
                val existing = if (request.query["prefix"] == "picked.bin") listOf("picked.bin") else emptyList()
                DemoReply.ok(buildJsonArray { existing.forEach { add(objectJson(it)) } }, resultInfo = truncatedAt(null))
            }
            put("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") {
                puts++
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = viewModelFor(api, PickedFile(size = 3L, bytes = "abc"))
        withTimeout(WAIT_MS) { viewModel.state.first { it.listing is UiState.Content } }

        // Act
        viewModel.onUploadPicked("content://picked/picked.bin")
        val waiting = withTimeout(WAIT_MS) { viewModel.state.first { it.pendingOverwrite != null } }
        val putsBeforeConfirm = puts
        viewModel.confirmOverwrite()
        withTimeout(WAIT_MS) { viewModel.transfers.first { list -> list.any { it.status != R2TransferStatus.RUNNING } } }

        // Assert
        assertEquals("picked.bin", waiting.pendingOverwrite?.key)
        assertEquals(0, putsBeforeConfirm)
        assertEquals(1, puts)
    }

    @Test
    fun `an upload to a free key starts without asking`() = runBlocking {
        // Arrange
        var puts = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") {
                DemoReply.ok(buildJsonArray {}, resultInfo = truncatedAt(null))
            }
            put("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") {
                puts++
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = viewModelFor(api, PickedFile(size = 3L, bytes = "abc"))
        withTimeout(WAIT_MS) { viewModel.state.first { it.listing is UiState.Content } }

        // Act
        viewModel.onUploadPicked("content://picked/picked.bin")
        withTimeout(WAIT_MS) { viewModel.transfers.first { list -> list.any { it.status != R2TransferStatus.RUNNING } } }

        // Assert
        assertEquals(null, viewModel.state.value.pendingOverwrite)
        assertEquals(1, puts)
    }

    @Test
    fun `loading the next page of a folder appends its objects and clears the cursor at the end`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") { request ->
                if (request.query["cursor"] == "page-2") {
                    DemoReply.ok(buildJsonArray { add(objectJson("second.txt")) }, resultInfo = truncatedAt(null))
                } else {
                    DemoReply.ok(buildJsonArray { add(objectJson("first.txt")) }, resultInfo = truncatedAt("page-2"))
                }
            }
        }
        val viewModel = viewModelFor(api, PickedDocument(size = 1L))
        withTimeout(WAIT_MS) { viewModel.state.first { it.nextCursor == "page-2" } }

        // Act
        viewModel.loadMore()
        val after = withTimeout(WAIT_MS) { viewModel.state.first { it.nextCursor == null && !it.isLoadingMore } }

        // Assert
        assertEquals(listOf("first.txt", "second.txt"), listedKeys(after))
    }

    @Test
    fun `a next page that fails keeps the objects already listed and keeps the cursor for a retry`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") { request ->
                if (request.query["cursor"] == "page-2") {
                    DemoReply.failure(HttpStatusCode.InternalServerError, 10000, "temporarily unavailable")
                } else {
                    DemoReply.ok(buildJsonArray { add(objectJson("first.txt")) }, resultInfo = truncatedAt("page-2"))
                }
            }
        }
        val viewModel = viewModelFor(api, PickedDocument(size = 1L))
        withTimeout(WAIT_MS) { viewModel.state.first { it.nextCursor == "page-2" } }

        // Act
        viewModel.loadMore()
        val after = withTimeout(WAIT_MS) { viewModel.state.first { it.loadMoreFailed } }

        // Assert
        assertEquals(listOf("first.txt"), listedKeys(after))
        assertEquals("page-2", after.nextCursor)
    }

    private fun listedKeys(state: R2ObjectsUiState): List<String> =
        (state.listing as UiState.Content).data.objects.map { it.key }

    private fun objectJson(key: String) = buildJsonObject {
        put("key", key)
        put("size", 1L)
    }

    private fun truncatedAt(cursor: String?) = buildJsonObject {
        put("is_truncated", cursor != null)
        if (cursor != null) put("cursor", cursor)
    }

    private fun viewModelFor(api: com.tomasps.virga.core.network.CloudflareApi, streams: R2DocumentStreams): R2ObjectsViewModel {
        val repository = R2Repository(api)
        val coordinator = R2TransferCoordinator(repository, streams, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        return R2ObjectsViewModel(FakeSessionRepository(), repository, coordinator, streams, BUCKET)
    }
}
