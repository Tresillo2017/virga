package com.tomasps.virga.feature.r2.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.r2.api.R2Repository
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT = "demo-account-personal"
private const val SEED_BUCKET = "demo-assets"
private const val NEW_BUCKET = "scratch-bucket"
private const val CREATED_AT = "2026-11-13T10:00:00Z"
private const val SEED_CREATED_ON = "2026-01-10T09:00:00Z"

class R2DemoRoutesTest {

    private val repository = R2Repository(mockCloudflareApi { R2DemoRoutes().contribute(this) })

    @Test
    fun `the demo account starts with the seeded buckets`() = runBlocking {
        val page = (repository.listBuckets(ACCOUNT, cursor = null, nameContains = null) as ApiResult.Success).value

        assertTrue(page.buckets.map { it.name }.contains(SEED_BUCKET))
        assertTrue(page.buckets.size >= 3)
    }

    @Test
    fun `a name filter narrows the bucket list`() = runBlocking {
        val page = (repository.listBuckets(ACCOUNT, cursor = null, nameContains = "backups") as ApiResult.Success).value

        assertEquals(listOf("demo-backups"), page.buckets.map { it.name })
    }

    @Test
    fun `creating a bucket twice reports a conflict the second time`() = runBlocking {
        val first = repository.createBucket(ACCOUNT, NEW_BUCKET, location = null, storageClass = null)
        val second = repository.createBucket(ACCOUNT, NEW_BUCKET, location = null, storageClass = null)

        assertTrue(first is ApiResult.Success)
        assertTrue(second is ApiResult.Failure)
    }

    @Test
    fun `a bucket that still holds objects cannot be deleted`() = runBlocking {
        val result = repository.deleteBucket(ACCOUNT, SEED_BUCKET)

        assertTrue(result is ApiResult.Failure)
        assertTrue(repository.bucket(ACCOUNT, SEED_BUCKET) is ApiResult.Success)
    }

    @Test
    fun `an empty bucket is deleted and then reads as not found`() = runBlocking {
        repository.createBucket(ACCOUNT, NEW_BUCKET, location = null, storageClass = null)

        val deleted = repository.deleteBucket(ACCOUNT, NEW_BUCKET)

        assertTrue(deleted is ApiResult.Success)
        assertTrue(repository.bucket(ACCOUNT, NEW_BUCKET) is ApiResult.Failure)
    }

    @Test
    fun `an uploaded object appears in the listing and can be deleted`() = runBlocking {
        val api = mockCloudflareApi { R2DemoRoutes().contribute(this) }
        val objects = R2Repository(api)
        val key = "docs/readme.txt"

        val bytes = byteArrayOf(1, 2, 3)
        objects.uploadObject(ACCOUNT, SEED_BUCKET, key, "text/plain", bytes.size.toLong()) { channel -> channel.writeFully(bytes) }
        val listed = (objects.listObjects(ACCOUNT, SEED_BUCKET, "docs/", delimiter = false, cursor = null) as ApiResult.Success).value
        assertTrue(listed.objects.any { it.key == key })

        objects.deleteObject(ACCOUNT, SEED_BUCKET, key)
        val after = (objects.listObjects(ACCOUNT, SEED_BUCKET, "docs/", delimiter = false, cursor = null) as ApiResult.Success).value
        assertFalse(after.objects.any { it.key == key })
    }

    @Test
    fun `a bucket created in the session is dated when it was created, not with the seed date`() = runBlocking {
        val stamped = R2Repository(mockCloudflareApi { R2DemoRoutes { CREATED_AT }.contribute(this) })

        stamped.createBucket(ACCOUNT, NEW_BUCKET, location = null, storageClass = null)

        val created = (stamped.bucket(ACCOUNT, NEW_BUCKET) as ApiResult.Success).value
        assertEquals(CREATED_AT, created.creationDate)
        val seeded = (stamped.bucket(ACCOUNT, SEED_BUCKET) as ApiResult.Success).value
        assertEquals(SEED_CREATED_ON, seeded.creationDate)
    }

    @Test
    fun `an upload is recorded with the content type its file extension implies`() = runBlocking {
        val bytes = byteArrayOf(9)
        repository.uploadObject(ACCOUNT, SEED_BUCKET, "pics/photo.PNG", null, 1) { channel -> channel.writeFully(bytes) }

        assertEquals("image/png", listedObject("pics/photo.PNG").contentType)
    }

    @Test
    fun `an upload with an unknown extension stays without a content type`() = runBlocking {
        repository.uploadObject(ACCOUNT, SEED_BUCKET, "data/blob.xyz", null, 1) { channel -> channel.writeFully(byteArrayOf(1)) }

        assertNull(listedObject("data/blob.xyz").contentType)
    }

    @Test
    fun `an upload is dated with the time of the upload`() = runBlocking {
        val stamped = R2Repository(mockCloudflareApi { R2DemoRoutes { CREATED_AT }.contribute(this) })
        stamped.uploadObject(ACCOUNT, SEED_BUCKET, "dated/file.txt", "text/plain", 1) { channel -> channel.writeFully(byteArrayOf(1)) }

        val listed = (stamped.listObjects(ACCOUNT, SEED_BUCKET, "dated/", delimiter = false, cursor = null) as ApiResult.Success).value
        assertEquals(CREATED_AT, listed.objects.single { it.key == "dated/file.txt" }.lastModified)
    }

    private suspend fun listedObject(key: String) = (repository.listObjects(ACCOUNT, SEED_BUCKET, key, delimiter = false, cursor = null) as ApiResult.Success)
        .value.objects.single { it.key == key }
}
