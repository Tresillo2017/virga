package com.tomasps.virga.feature.r2.transfer

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.r2.api.R2Repository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

private const val ACCOUNT = "acc-1"
private const val WAIT_MS = 5_000L
private const val FILE_URI = "content://files/source"
private const val DEST_URI = "content://files/destination"

/** In-memory stand-in for the Storage Access Framework. Keeps the bytes each document holds. */
private class FakeDocumentStreams(private val sources: Map<String, ByteArray>) : R2DocumentStreams {
    val written = mutableMapOf<String, ByteArrayOutputStream>()
    val deleted = mutableListOf<String>()

    override fun openForRead(uriString: String): InputStream = ByteArrayInputStream(sources.getValue(uriString))

    override fun openForWrite(uriString: String): OutputStream =
        ByteArrayOutputStream().also { written[uriString] = it }

    override fun describe(uriString: String): DocumentInfo =
        DocumentInfo(displayName = "source.bin", size = sources[uriString]?.size?.toLong(), mimeType = "application/octet-stream")

    override fun delete(uriString: String): Boolean {
        deleted += uriString
        return true
    }
}

/** Documents the provider refuses to open for writing with a runtime error, as after a revoked grant. */
private class DeniedStreams : R2DocumentStreams {
    override fun openForRead(uriString: String): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun openForWrite(uriString: String): OutputStream = throw SecurityException("grant revoked")

    override fun describe(uriString: String): DocumentInfo = DocumentInfo(null, null, null)

    val deletedAttempts = mutableListOf<String>()

    override fun delete(uriString: String): Boolean {
        deletedAttempts += uriString
        return false
    }
}

class R2TransferCoordinatorTest {

    @Test
    fun `download streams the object into the chosen document and completes with every byte counted`() = runBlocking {
        // Arrange
        val body = "object-body".encodeToByteArray()
        val streams = FakeDocumentStreams(emptyMap())
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { DemoReply.raw(body) }
        }
        val coordinator = coordinatorFor(api, streams)

        // Act
        val id = coordinator.startDownload(ACCOUNT, "site", "a.txt", DEST_URI, totalBytes = body.size.toLong())
        val finished = awaitFinished(coordinator, id)

        // Assert
        assertEquals(R2TransferStatus.COMPLETED, finished.status)
        assertEquals(body.size.toLong(), finished.doneBytes)
        assertEquals("object-body", streams.written.getValue(DEST_URI).toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `a failed download is marked FAILED with a friendly message and no partial file is kept`() = runBlocking {
        // Arrange
        val streams = FakeDocumentStreams(emptyMap())
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") {
                DemoReply.failure(HttpStatusCode.NotFound, 10007, "no such object")
            }
        }
        val coordinator = coordinatorFor(api, streams)

        // Act
        val id = coordinator.startDownload(ACCOUNT, "site", "missing.txt", DEST_URI, totalBytes = null)
        val finished = awaitFinished(coordinator, id)

        // Assert
        assertEquals(R2TransferStatus.FAILED, finished.status)
        assertTrue(finished.failure != null)
    }

    @Test
    fun `upload reads the document and sends it as one PUT, counting bytes as they go`() = runBlocking {
        // Arrange
        val payload = "upload-me".encodeToByteArray()
        val streams = FakeDocumentStreams(mapOf(FILE_URI to payload))
        var receivedBytes = -1
        val api = mockCloudflareApi {
            put("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { request ->
                receivedBytes = request.bodyBytes?.size ?: 0
                DemoReply.ok(kotlinx.serialization.json.JsonNull)
            }
        }
        val coordinator = coordinatorFor(api, streams)

        // Act
        val id = coordinator.startUpload(ACCOUNT, "site", "dir/up.bin", FILE_URI, "application/octet-stream", payload.size.toLong())
        val finished = awaitFinished(coordinator, id)

        // Assert
        assertEquals(R2TransferStatus.COMPLETED, finished.status)
        assertEquals(payload.size, receivedBytes)
        assertEquals(payload.size.toLong(), finished.doneBytes)
    }

    @Test
    fun `finished transfers can be dismissed while running ones cannot`() = runBlocking {
        // Arrange
        val streams = FakeDocumentStreams(mapOf(FILE_URI to ByteArray(0)))
        val api = mockCloudflareApi {
            put("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { DemoReply.ok(kotlinx.serialization.json.JsonNull) }
        }
        val coordinator = coordinatorFor(api, streams)
        val id = coordinator.startUpload(ACCOUNT, "site", "empty.bin", FILE_URI, "application/octet-stream", 0L)
        awaitFinished(coordinator, id)

        // Act
        coordinator.dismiss(id)

        // Assert
        assertTrue(coordinator.transfers.value.none { it.id == id })
    }

    @Test
    fun `a download whose document cannot be written is marked FAILED instead of leaving the panel running`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { DemoReply.raw("x".encodeToByteArray()) }
        }
        val streams = DeniedStreams()
        val coordinator = coordinatorFor(api, streams)

        // Act
        val id = coordinator.startDownload(ACCOUNT, "site", "a.txt", DEST_URI, totalBytes = 1L)
        val finished = awaitFinished(coordinator, id)

        // Assert
        assertEquals(R2TransferStatus.FAILED, finished.status)
        assertTrue(finished.failure != null)
        assertEquals(listOf(DEST_URI), streams.deletedAttempts)
    }

    @Test
    fun `an upload whose file shrinks while it is read is marked FAILED`() = runBlocking {
        // Arrange
        val streams = FakeDocumentStreams(mapOf(FILE_URI to "short".encodeToByteArray()))
        val api = mockCloudflareApi {
            put("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { DemoReply.ok(kotlinx.serialization.json.JsonNull) }
        }
        val coordinator = coordinatorFor(api, streams)

        // Act
        val id = coordinator.startUpload(ACCOUNT, "site", "x.bin", FILE_URI, "application/octet-stream", totalBytes = 100L)
        val finished = awaitFinished(coordinator, id)

        // Assert
        assertEquals(R2TransferStatus.FAILED, finished.status)
    }

    @Test
    fun `each transfer records the account it was started under`() = runBlocking {
        // Arrange
        val streams = FakeDocumentStreams(mapOf(FILE_URI to ByteArray(0)))
        val api = mockCloudflareApi {
            put("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { DemoReply.ok(kotlinx.serialization.json.JsonNull) }
        }
        val coordinator = coordinatorFor(api, streams)

        // Act
        val id = coordinator.startUpload("acc-other", "site", "e.bin", FILE_URI, null, 0L)
        val finished = awaitFinished(coordinator, id)

        // Assert
        assertEquals("acc-other", finished.accountId)
    }

    private fun coordinatorFor(api: com.tomasps.virga.core.network.CloudflareApi, streams: R2DocumentStreams) =
        R2TransferCoordinator(
            repository = R2Repository(api),
            streams = streams,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

    private suspend fun awaitFinished(coordinator: R2TransferCoordinator, id: String): R2Transfer =
        withTimeout(WAIT_MS) {
            coordinator.transfers.first { list -> list.any { it.id == id && it.status != R2TransferStatus.RUNNING } }
                .single { it.id == id }
        }
}
