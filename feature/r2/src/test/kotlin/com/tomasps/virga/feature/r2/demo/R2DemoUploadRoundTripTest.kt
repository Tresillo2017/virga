package com.tomasps.virga.feature.r2.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.r2.api.R2Repository
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Test

private const val ROUND_TRIP_ACCOUNT = "demo-account-personal"
private const val ROUND_TRIP_BUCKET = "demo-assets"

class R2DemoUploadRoundTripTest {

    @Test
    fun `a downloaded object returns exactly the bytes that were uploaded`() = runBlocking {
        // Arrange
        val repository = R2Repository(mockCloudflareApi { R2DemoRoutes().contribute(this) })
        val key = "uploads/round-trip.txt"
        val bytes = "virga upload test\nline two\n".toByteArray()
        repository.uploadObject(ROUND_TRIP_ACCOUNT, ROUND_TRIP_BUCKET, key, "text/plain", bytes.size.toLong()) { channel ->
            channel.writeFully(bytes)
        }

        // Act
        val downloaded = repository.downloadObject<ByteArray>(ROUND_TRIP_ACCOUNT, ROUND_TRIP_BUCKET, key) { channel, _ ->
            channel.readRemaining().readBytes()
        }

        // Assert
        assertArrayEquals(bytes, (downloaded as ApiResult.Success).value)
    }
}
