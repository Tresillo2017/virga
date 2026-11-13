package com.tomasps.virga.feature.kv.demo

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.kv.data.KV_MULTIPART_BOUNDARY
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.testing.TEST_NOW_EPOCH_SECONDS
import com.tomasps.virga.feature.kv.value.KV_MAX_VALUE_BYTES
import com.tomasps.virga.feature.kv.value.KvExpiration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT = KV_DEMO_ACCOUNT_PERSONAL
private const val NAMESPACE = KV_DEMO_NS_SESSIONS
private const val ONE_HOUR = 3_600L
private const val LIST_LIMIT = 10
private const val CURSOR_KEY_COUNT = 25

/**
 * The demo routes behind the real KvRepository. These tests prove the path encoding, the multipart write and the
 * paging work end to end, not only against hand-written mock replies.
 */
class KvDemoRoutesTest {

    private fun repositoryWith(store: KvDemoStore = KvDemoStore(TEST_NOW_EPOCH_SECONDS)): KvRepository {
        val api = mockCloudflareApi {
            KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this)
        }
        return KvRepository(api)
    }

    @Test
    fun `a key with slashes is written, listed and read back as one name`() = runBlocking {
        // Arrange
        val repository = repositoryWith()
        val value = "hello".encodeToByteArray()

        // Act
        val written = repository.writeValue(ACCOUNT, NAMESPACE, "users/42/profile", value, null, KvExpiration.None)
        val page = (repository.keyPage(ACCOUNT, NAMESPACE, prefix = "users/", cursor = null, limit = LIST_LIMIT) as ApiResult.Success).value
        val read = (repository.readValue(ACCOUNT, NAMESPACE, "users/42/profile", KV_MAX_VALUE_BYTES) as ApiResult.Success).value

        // Assert
        assertTrue(written is ApiResult.Success)
        assertEquals(listOf("users/42/profile"), page.keys.map { it.name })
        assertArrayEquals(value, read)
    }

    @Test
    fun `binary values survive the multipart write, including CRLF bytes inside the value`() = runBlocking {
        // Arrange
        val repository = repositoryWith()
        val value = byteArrayOf(0x89.toByte(), 0x0D, 0x0A, 0x00, 0xFF.toByte(), 0x0D, 0x0A, 0x2D, 0x2D)

        // Act
        repository.writeValue(ACCOUNT, NAMESPACE, "blobs/raw.bin", value, null, KvExpiration.None)
        val read = (repository.readValue(ACCOUNT, NAMESPACE, "blobs/raw.bin", KV_MAX_VALUE_BYTES) as ApiResult.Success).value

        // Assert
        assertArrayEquals(value, read)
    }

    @Test
    fun `metadata and a ttl written with a value are read back`() = runBlocking {
        // Arrange
        val repository = repositoryWith()
        val metadata = buildJsonObject { put("owner", "demo-team") }

        // Act
        repository.writeValue(ACCOUNT, NAMESPACE, "with/meta", "v".encodeToByteArray(), metadata, KvExpiration.Ttl(ONE_HOUR))
        val stored = (repository.metadata(ACCOUNT, NAMESPACE, "with/meta") as ApiResult.Success).value
        val found = (repository.findKey(ACCOUNT, NAMESPACE, "with/meta") as ApiResult.Success).value

        // Assert
        assertEquals(metadata, stored)
        assertEquals(TEST_NOW_EPOCH_SECONDS + ONE_HOUR, found?.expirationEpochSeconds)
    }

    @Test
    fun `a missing key reads as not found, and its metadata reads as none`() = runBlocking {
        // Arrange
        val repository = repositoryWith()

        // Act
        val read = repository.readValue(ACCOUNT, NAMESPACE, "does/not/exist", KV_MAX_VALUE_BYTES)
        val metadata = repository.metadata(ACCOUNT, NAMESPACE, "does/not/exist")

        // Assert
        assertTrue(read is ApiResult.Failure && read.error is ApiError.NotFound)
        assertNull((metadata as ApiResult.Success).value)
    }

    @Test
    fun `a key named dot or dot-dot is refused before any request is sent`() = runBlocking {
        // Arrange
        val repository = repositoryWith()

        // Act
        val result = repository.writeValue(ACCOUNT, NAMESPACE, ".", "x".encodeToByteArray(), null, KvExpiration.None)

        // Assert
        assertTrue(result is ApiResult.Failure && result.error is ApiError.Local)
    }

    @Test
    fun `key pages follow the cursor until the last page`() = runBlocking {
        // Arrange
        val repository = repositoryWith()
        val pairs = buildJsonArray {
            repeat(CURSOR_KEY_COUNT) { index ->
                add(buildJsonObject { put("key", "page/%02d".format(index)); put("value", "v") })
            }
        }
        repository.bulkWrite(ACCOUNT, NAMESPACE, pairs)

        // Act
        val first = (repository.keyPage(ACCOUNT, NAMESPACE, "page/", cursor = null, limit = LIST_LIMIT) as ApiResult.Success).value
        val second = (repository.keyPage(ACCOUNT, NAMESPACE, "page/", cursor = first.nextCursor, limit = LIST_LIMIT) as ApiResult.Success).value
        val third = (repository.keyPage(ACCOUNT, NAMESPACE, "page/", cursor = second.nextCursor, limit = LIST_LIMIT) as ApiResult.Success).value

        // Assert
        assertEquals(LIST_LIMIT, first.keys.size)
        assertEquals(LIST_LIMIT, second.keys.size)
        assertEquals(CURSOR_KEY_COUNT - 2 * LIST_LIMIT, third.keys.size)
        assertNull(third.nextCursor)
        assertTrue(first.nextCursor != null)
    }

    @Test
    fun `bulk write then bulk delete changes what the list shows`() = runBlocking {
        // Arrange
        val repository = repositoryWith()
        val pairs = buildJsonArray {
            add(buildJsonObject { put("key", "bulk/a"); put("value", "1") })
            add(buildJsonObject { put("key", "bulk/b"); put("value", "2") })
        }

        // Act
        val written = (repository.bulkWrite(ACCOUNT, NAMESPACE, pairs) as ApiResult.Success).value
        val deleted = (repository.bulkDelete(ACCOUNT, NAMESPACE, listOf("bulk/a")) as ApiResult.Success).value
        val remaining = (repository.keyPage(ACCOUNT, NAMESPACE, "bulk/", cursor = null, limit = LIST_LIMIT) as ApiResult.Success).value

        // Assert
        assertEquals(2, written.successfulCount)
        assertEquals(1, deleted.successfulCount)
        assertEquals(listOf("bulk/b"), remaining.keys.map { it.name })
    }

    @Test
    fun `namespace create, rename and delete go through the storage kv routes`() = runBlocking {
        // Arrange
        val repository = repositoryWith()

        // Act
        val created = (repository.createNamespace(ACCOUNT, "Scratch") as ApiResult.Success).value
        repository.renameNamespace(ACCOUNT, created.id, "Scratch two")
        val renamed = (repository.namespace(ACCOUNT, created.id) as ApiResult.Success).value
        repository.deleteNamespace(ACCOUNT, created.id)
        val remaining = (repository.namespaces(ACCOUNT) as ApiResult.Success).value

        // Assert
        assertEquals("Scratch two", renamed.title)
        assertTrue(remaining.none { it.id == created.id })
    }

    @Test
    fun `deleting a value that is already gone still succeeds`() = runBlocking {
        // Arrange
        val repository = repositoryWith()

        // Act
        val result = repository.deleteValue(ACCOUNT, NAMESPACE, "never/existed")

        // Assert
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `the demo rejects an account that is not a demo account`() = runBlocking {
        // Arrange
        val repository = repositoryWith()

        // Act
        val result = repository.namespaces("some-real-account")

        // Assert
        assertTrue(result is ApiResult.Failure && result.error is ApiError.NotFound)
    }

    @Test
    fun `the seeded demo data includes a key with odd characters and a binary value`() = runBlocking {
        // Arrange
        val repository = repositoryWith()

        // Act
        val odd = (repository.findKey(ACCOUNT, KV_DEMO_NS_FLAGS, "a b+c") as ApiResult.Success).value
        val binary = (repository.readValue(ACCOUNT, KV_DEMO_NS_ASSETS, "blobs/thumb.bin", KV_MAX_VALUE_BYTES) as ApiResult.Success).value

        // Assert
        assertEquals("a b+c", odd?.name)
        assertTrue(binary != null && binary.first() == 0x89.toByte())
    }

    @Test
    fun `json null metadata is treated as none`() = runBlocking {
        // Arrange
        val repository = repositoryWith()
        repository.writeValue(ACCOUNT, NAMESPACE, "plain", "v".encodeToByteArray(), JsonNull, KvExpiration.None)

        // Act
        val metadata = (repository.metadata(ACCOUNT, NAMESPACE, "plain") as ApiResult.Success).value

        // Assert
        assertNull(metadata)
    }

    @Test
    fun `a value within the 25 MiB limit is read in full`() = runBlocking {
        // Arrange
        val repository = repositoryWith()
        val value = ByteArray(KV_MAX_VALUE_BYTES.toInt()) { (it % 251).toByte() }
        repository.writeValue(ACCOUNT, NAMESPACE, "big", value, null, KvExpiration.None)

        // Act
        val read = (repository.readValue(ACCOUNT, NAMESPACE, "big", KV_MAX_VALUE_BYTES) as ApiResult.Success).value

        // Assert
        assertEquals(value.size, read?.size)
        assertArrayEquals(value, read)
    }

    @Test
    fun `json primitive names in bulk items are used verbatim`() {
        // Arrange
        val text = """[{"key":"a/b c","value":"x","expiration_ttl":120}]"""

        // Act
        val pairs = parseKvBulkPairs(text, TEST_NOW_EPOCH_SECONDS)

        // Assert
        assertEquals("a/b c", pairs.single().key)
        assertEquals(TEST_NOW_EPOCH_SECONDS + 120, pairs.single().expiresAt)
        assertEquals("x", String(pairs.single().value))
    }

    @Test
    fun `a multipart body without metadata gives no metadata`() {
        // Arrange
        val body = "--$KV_MULTIPART_BOUNDARY\r\nContent-Disposition: form-data; name=\"value\"\r\n\r\nhi\r\n--$KV_MULTIPART_BOUNDARY--\r\n".encodeToByteArray()

        // Act
        val write = parseKvWriteBody(body)

        // Assert
        assertEquals("hi", String(write.value))
        assertNull(write.metadata)
    }

    @Test
    fun `an empty array body is an empty bulk write`() {
        // Act
        val pairs = parseKvBulkPairs("[]", TEST_NOW_EPOCH_SECONDS)
        val names = parseKvDeleteNames("[]")

        // Assert
        assertTrue(pairs.isEmpty())
        assertTrue(names.isEmpty())
    }
}
