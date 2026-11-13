package com.tomasps.virga.feature.kv.key

import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoRoutes
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.testing.TEST_NOW_EPOCH_SECONDS
import com.tomasps.virga.feature.kv.value.KvExpiration
import com.tomasps.virga.feature.kv.value.KvWriteThrottle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ONE_SECOND_MILLIS = 1_000L
private const val SHORT_GAP_MILLIS = 400L
private const val ONE_HOUR = 3_600L
private const val TOO_SOON = 30L
private const val KEY = "throttled/key"

class KvKeyWriterTest {

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )

    @Test
    fun `a second write to the same key within a second is refused, and no request is sent`() = runBlocking {
        // Arrange
        var millis = TEST_NOW_EPOCH_SECONDS * 1_000L
        val writer = KvKeyWriter(repository, KvWriteThrottle { millis }) { TEST_NOW_EPOCH_SECONDS }
        val bytes = "one".encodeToByteArray()

        // Act
        val first = writer.write(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, KEY, bytes, null, KvExpiration.None)
        millis += SHORT_GAP_MILLIS
        val second = writer.write(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, KEY, "two".encodeToByteArray(), null, KvExpiration.None)

        // Assert
        assertTrue(first is KvWriteResult.Written)
        assertTrue(second is KvWriteResult.Throttled)
        assertEquals(ONE_SECOND_MILLIS - SHORT_GAP_MILLIS, (second as KvWriteResult.Throttled).remainingMillis)
        assertEquals("one", String(store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, KEY))!!.bytes))
    }

    @Test
    fun `a key with no expiration keeps no expiration`() {
        // Arrange
        val writer = KvKeyWriter(repository, KvWriteThrottle()) { TEST_NOW_EPOCH_SECONDS }

        // Act
        val keep = writer.expirationToKeep(null)

        // Assert
        assertEquals(KvExpirationKeep.Keep(KvExpiration.None), keep)
    }

    @Test
    fun `a key expiring in less than a minute cannot be edited`() {
        // Arrange
        val writer = KvKeyWriter(repository, KvWriteThrottle()) { TEST_NOW_EPOCH_SECONDS }

        // Act
        val keep = writer.expirationToKeep(TEST_NOW_EPOCH_SECONDS + TOO_SOON)

        // Assert
        assertEquals(KvExpirationKeep.TooSoon, keep)
    }

    @Test
    fun `a key expiring later keeps its absolute expiration`() {
        // Arrange
        val writer = KvKeyWriter(repository, KvWriteThrottle()) { TEST_NOW_EPOCH_SECONDS }

        // Act
        val keep = writer.expirationToKeep(TEST_NOW_EPOCH_SECONDS + ONE_HOUR)

        // Assert
        assertEquals(KvExpirationKeep.Keep(KvExpiration.Absolute(TEST_NOW_EPOCH_SECONDS + ONE_HOUR)), keep)
    }
}
