package com.tomasps.virga.feature.r2.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.r2.api.R2BucketMetrics
import com.tomasps.virga.feature.r2.api.R2MetricsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val ACCOUNT = "demo-account-personal"
private const val BUCKET = "demo-assets"
private val START: Instant = Instant.parse("2026-11-01T00:00:00Z")

class R2DemoMetricsTest {

    private fun fetch(bucket: String, end: Instant): R2BucketMetrics = runBlocking {
        val api = mockCloudflareApi { R2DemoRoutes().contribute(this) }
        val result = R2MetricsRepository(api).bucketMetrics(ACCOUNT, bucket, START, end)
        (result as ApiResult.Success).value
    }

    @Test
    fun `a three day window returns one storage point per day and every action type`() {
        val metrics = fetch(BUCKET, START.plus(3, ChronoUnit.DAYS))

        assertEquals(3, metrics.storage.size)
        assertEquals(listOf("GetObject", "PutObject", "ListBucket", "HeadObject", "DeleteObject"), metrics.operations.map { it.action })
        assertTrue(metrics.operations.all { it.requests > 0 })
    }

    @Test
    fun `the window is capped at ninety days however long the request asks for`() {
        val metrics = fetch(BUCKET, START.plus(400, ChronoUnit.DAYS))

        assertEquals(90, metrics.storage.size)
    }

    @Test
    fun `object counts grow day by day so the storage chart rises`() {
        val storage = fetch(BUCKET, START.plus(10, ChronoUnit.DAYS)).storage

        assertTrue(storage.zipWithNext().all { (a, b) -> b.objectCount > a.objectCount })
    }

    @Test
    fun `the same bucket always gets the same numbers and another bucket gets different ones`() {
        val first = fetch(BUCKET, START.plus(5, ChronoUnit.DAYS))
        val again = fetch(BUCKET, START.plus(5, ChronoUnit.DAYS))
        val other = fetch("demo-logs-archive", START.plus(5, ChronoUnit.DAYS))

        assertEquals(first, again)
        assertTrue(first.operations != other.operations)
    }
}
