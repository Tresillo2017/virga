package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R2RepositoryKeysTest {

    @Test
    fun `keys with slashes are safe while empty keys and dot segments are not`() {
        // Arrange
        val safe = "photos/2026/cat.jpg"
        val dotSegment = "photos/../secrets.txt"
        val singleDot = "./index.html"

        // Act
        val results = listOf(isSafeObjectKey(safe), isSafeObjectKey(dotSegment), isSafeObjectKey(singleDot), isSafeObjectKey(""))

        // Assert
        assertEquals(listOf(true, false, false, false), results)
    }

    @Test
    fun `delete refuses a dot segment locally and sends no request`() = runBlocking {
        // Arrange
        var requests = 0
        val api = mockCloudflareApi {
            delete("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") {
                requests++
                DemoReply.empty()
            }
        }

        // Act
        val result = R2Repository(api).deleteObject("acc-1", "site", "docs/../keep.txt")

        // Assert
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Local)
        assertEquals(0, requests)
    }

    @Test
    fun `a key that is safe is still sent to the object path`() = runBlocking {
        // Arrange
        var requests = 0
        val api = mockCloudflareApi {
            delete("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") {
                requests++
                DemoReply.empty()
            }
        }

        // Act
        val result = R2Repository(api).deleteObject("acc-1", "site", "docs/keep.txt")

        // Assert
        assertTrue(result is ApiResult.Success)
        assertEquals(1, requests)
        assertFalse(requests == 0)
    }
}
