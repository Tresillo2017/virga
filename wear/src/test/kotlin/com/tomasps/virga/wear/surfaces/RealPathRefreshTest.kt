package com.tomasps.virga.wear.surfaces

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.network.RateLimiter
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.wear.builds.WearBuildsSource
import com.tomasps.virga.wear.ui.ACCOUNT
import com.tomasps.virga.wear.ui.CONNECTED
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

private const val NANOS_PER_MILLI = 1_000_000L
private const val SCRIPTS = "/accounts/{accountId}/workers/scripts"
private const val BUILDS = "/accounts/{accountId}/builds/workers/{workerTag}/builds"

/**
 * The refresher over a real [WearBuildsSource] and a mock Cloudflare API. [WearBuildsSource.recent] tolerates a failed
 * Worker for the list screen; the surfaces must not, or a rate limit would read as "no builds".
 */
class RealPathRefreshTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val t0 = Instant.parse("2026-11-14T12:00:00Z")
    private val t1 = Instant.parse("2026-11-14T12:20:00Z")
    private val cache by lazy {
        LatestBuildCache(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "c.preferences_pb") })
    }
    private val cached = BuildSummary(
        buildUuid = "cached", scriptName = "old", workerTag = "tag-old", status = BuildStatus.STOPPED,
        createdOn = Instant.parse("2026-11-14T09:00:00Z"),
    )

    @After
    fun tearDown() = scope.cancel()

    /** A rate limiter on a fake clock, so a 429's Retry-After block passes instantly instead of waiting in real time. */
    private fun fastRateLimiter(): RateLimiter {
        var nanos = 0L
        return RateLimiter(nowNanos = { nanos }, delayMillis = { nanos += it * NANOS_PER_MILLI })
    }

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun scripts(vararg names: String) =
        json(names.joinToString(prefix = "[", postfix = "]") { """{"id":"$it","tag":"tag-$it"}""" })

    private fun buildJson(id: String, created: String) =
        """{"build_uuid":"$id","status":"stopped","build_outcome":"success","created_on":"$created"}"""

    private fun refresherFor(api: com.tomasps.virga.core.network.CloudflareApi, at: Instant = t1) =
        BuildSurfaceRefresher(
            WearBuildsSource(BuildsRepository(api), onUnauthorized = {}, hasToken = { true }),
            MutableStateFlow(CONNECTED),
            cache,
            warn = {},
        ) { at }

    @Test
    fun rateLimitedBuildCallsReturnTheCachedBuildAsStaleAndKeepTheCache() = runTest {
        cache.save(ACCOUNT, false, cached, t0)
        val api = mockCloudflareApi(rateLimiter = fastRateLimiter()) {
            get(SCRIPTS) { DemoReply.ok(scripts("a", "b")) }
            get(BUILDS) { DemoReply.failure(HttpStatusCode.TooManyRequests, 971, "rate limited") }
        }

        val data = refresherFor(api).refresh()

        assertEquals(SurfaceStatus.Stale, data.status)
        assertEquals("cached", data.latest!!.buildUuid)
        assertEquals(t0, data.fetchedAt)
        assertNotNull(cache.load())
    }

    @Test
    fun serverErrorOnBuildCallsReturnsTheCachedBuildAsStale() = runTest {
        cache.save(ACCOUNT, false, cached, t0)
        val api = mockCloudflareApi(rateLimiter = fastRateLimiter()) {
            get(SCRIPTS) { DemoReply.ok(scripts("a")) }
            get(BUILDS) { DemoReply.failure(HttpStatusCode.ServiceUnavailable, 1, "down") }
        }

        val data = refresherFor(api).refresh()

        assertEquals(SurfaceStatus.Stale, data.status)
        assertEquals("cached", data.latest!!.buildUuid)
        assertNotNull(cache.load())
    }

    @Test
    fun newestWorkerFailingDoesNotLetAnOlderBuildPassAsFresh() = runTest {
        cache.save(ACCOUNT, false, cached, t0)
        val api = mockCloudflareApi(rateLimiter = fastRateLimiter()) {
            get(SCRIPTS) { DemoReply.ok(scripts("new", "older")) }
            get(BUILDS) { request ->
                if (request.pathParams["workerTag"] == "tag-new") {
                    DemoReply.failure(HttpStatusCode.TooManyRequests, 971, "rate limited")
                } else {
                    DemoReply.ok(json("[${buildJson("older-1", "2026-11-14T08:00:00Z")}]"))
                }
            }
        }

        val data = refresherFor(api).refresh()

        assertEquals(SurfaceStatus.Stale, data.status)
        assertEquals("cached", data.latest!!.buildUuid)
        assertEquals(t0, data.fetchedAt)
        assertEquals("cached", cache.load()!!.build.buildUuid)
    }

    @Test
    fun accountWithNoBuildsAtAllIsFreshAndClearsTheCache() = runTest {
        cache.save(ACCOUNT, false, cached, t0)
        val api = mockCloudflareApi(rateLimiter = fastRateLimiter()) {
            get(SCRIPTS) { DemoReply.ok(scripts("a", "b")) }
            get(BUILDS) { DemoReply.ok(json("[]")) }
        }

        val data = refresherFor(api).refresh()

        assertEquals(SurfaceData(null, t1, SurfaceStatus.Fresh), data)
        assertNull(cache.load())
    }

    @Test
    fun allWorkersSucceedingReturnsTheNewestAsFresh() = runTest {
        val api = mockCloudflareApi(rateLimiter = fastRateLimiter()) {
            get(SCRIPTS) { DemoReply.ok(scripts("a", "b")) }
            get(BUILDS) { request ->
                val created = if (request.pathParams["workerTag"] == "tag-a") "2026-11-14T10:00:00Z" else "2026-11-14T11:00:00Z"
                DemoReply.ok(json("[${buildJson("${request.pathParams["workerTag"]}-1", created)}]"))
            }
        }

        val data = refresherFor(api).refresh()

        assertEquals("tag-b-1", data.latest!!.buildUuid)
        assertEquals(SurfaceStatus.Fresh, data.status)
    }

    @Test
    fun workerWithoutABuildsConnectionIsSkippedWhenAnotherHasABuild() = runTest {
        val api = mockCloudflareApi(rateLimiter = fastRateLimiter()) {
            get(SCRIPTS) { DemoReply.ok(scripts("none", "ok")) }
            get(BUILDS) { request ->
                if (request.pathParams["workerTag"] == "tag-none") {
                    DemoReply.failure(HttpStatusCode.NotFound, 10000, "not found")
                } else {
                    DemoReply.ok(json("[${buildJson("ok-1", "2026-11-14T08:00:00Z")}]"))
                }
            }
        }

        val data = refresherFor(api).refresh()

        assertEquals(SurfaceStatus.Fresh, data.status)
        assertEquals("ok-1", data.latest!!.buildUuid)
    }

    @Test
    fun notFoundOnEveryWorkerIsFreshWithNoBuild() = runTest {
        cache.save(ACCOUNT, false, cached, t0)
        val api = mockCloudflareApi(rateLimiter = fastRateLimiter()) {
            get(SCRIPTS) { DemoReply.ok(scripts("a", "b")) }
            get(BUILDS) { DemoReply.failure(HttpStatusCode.NotFound, 10000, "not found") }
        }

        val data = refresherFor(api).refresh()

        assertEquals(SurfaceData(null, t1, SurfaceStatus.Fresh), data)
        assertNull(cache.load())
    }

    @Test
    fun rateLimitOnOneWorkerStillFailsEvenWhenAnotherIsNotFound() = runTest {
        cache.save(ACCOUNT, false, cached, t0)
        val api = mockCloudflareApi(rateLimiter = fastRateLimiter()) {
            get(SCRIPTS) { DemoReply.ok(scripts("none", "limited")) }
            get(BUILDS) { request ->
                if (request.pathParams["workerTag"] == "tag-none") {
                    DemoReply.failure(HttpStatusCode.NotFound, 10000, "not found")
                } else {
                    DemoReply.failure(HttpStatusCode.TooManyRequests, 971, "rate limited")
                }
            }
        }

        val data = refresherFor(api).refresh()

        assertEquals(SurfaceStatus.Stale, data.status)
        assertEquals("cached", data.latest!!.buildUuid)
    }
}
