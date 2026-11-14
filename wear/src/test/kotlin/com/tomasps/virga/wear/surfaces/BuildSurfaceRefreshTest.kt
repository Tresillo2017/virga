package com.tomasps.virga.wear.surfaces

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.wear.session.WatchSessionState
import com.tomasps.virga.wear.ui.ACCOUNT
import com.tomasps.virga.wear.ui.CONNECTED
import com.tomasps.virga.wear.ui.FakeWearBuilds
import com.tomasps.virga.wear.ui.build
import com.tomasps.virga.wear.ui.failure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class BuildSurfaceRefreshTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val t0 = Instant.parse("2026-11-14T12:00:00Z")
    private val t1 = Instant.parse("2026-11-14T12:20:00Z")
    private var now = t0
    private val warnings = mutableListOf<String>()
    private val session = MutableStateFlow<WatchSessionState>(CONNECTED)
    private val source = FakeWearBuilds()
    private val cache by lazy {
        LatestBuildCache(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "c.preferences_pb") })
    }
    private val refresher by lazy { BuildSurfaceRefresher(source, session, cache, warn = warnings::add) { now } }

    @After
    fun tearDown() = scope.cancel()

    /** The cache keeps only what the surfaces show, so the commit hash does not come back. */
    private fun BuildSummary.asCached() = copy(commitHash = null)

    private fun succeedWith(vararg builds: BuildSummary) {
        source.recent = ApiResult.Success(builds.toList())
    }

    @Test
    fun successfulRefreshSavesAndReturnsFresh() = runTest {
        val newest = build("b-9")
        succeedWith(newest, build("b-8"))

        val data = refresher.refresh()

        assertEquals(SurfaceData(newest, t0, SurfaceStatus.Fresh), data)
        assertEquals(newest.asCached(), cache.load()!!.build)
        assertEquals(listOf("latest:$ACCOUNT"), source.calls)
    }

    @Test
    fun rateLimitedRefreshReturnsLastCachedAsStale() = runTest {
        val cached = build("b-1")
        succeedWith(cached)
        refresher.refresh()
        now = t1
        source.recent = failure(ApiError.RateLimited(retryAfterMillis = 1000))

        assertEquals(SurfaceData(cached.asCached(), t0, SurfaceStatus.Stale), refresher.refresh())
    }

    @Test
    fun serverErrorRefreshReturnsLastCachedAsStale() = runTest {
        val cached = build("b-1")
        succeedWith(cached)
        refresher.refresh()
        now = t1
        source.recent = failure(ApiError.Server(503))

        assertEquals(SurfaceData(cached.asCached(), t0, SurfaceStatus.Stale), refresher.refresh())
    }

    @Test
    fun networkErrorRefreshReturnsLastCachedAsStale() = runTest {
        val cached = build("b-1")
        succeedWith(cached)
        refresher.refresh()
        source.recent = failure(ApiError.Network("offline"))

        assertEquals(SurfaceData(cached.asCached(), t0, SurfaceStatus.Stale), refresher.refresh())
    }

    @Test
    fun failureWithEmptyCacheReturnsStaleWithNoBuild() = runTest {
        source.recent = failure(ApiError.Server(500))

        assertEquals(SurfaceData(null, null, SurfaceStatus.Stale), refresher.refresh())
    }

    @Test
    fun emptyBuildListReturnsFreshWithNoBuild() = runTest {
        succeedWith(build("b-1"))
        refresher.refresh()
        source.latest = ApiResult.Success(null)
        now = t1

        assertEquals(SurfaceData(null, t1, SurfaceStatus.Fresh), refresher.refresh())
        assertNull(cache.load())
    }

    @Test
    fun cachedBuildOfAnotherAccountIsNeverShown() = runTest {
        cache.save("acc-other", false, build("b-other"), t0)
        source.recent = failure(ApiError.Server(500))

        assertEquals(SurfaceData(null, null, SurfaceStatus.Stale), refresher.refresh())
    }

    @Test
    fun notConnectedSessionReturnsNotConnectedWithoutNetwork() = runTest {
        cache.save(ACCOUNT, false, build("b-1"), t0)
        session.value = WatchSessionState.NotConnected

        assertEquals(SurfaceData(null, null, SurfaceStatus.NotConnected), refresher.refresh())
        assertTrue(source.calls.isEmpty())
        assertNull(cache.load())
    }

    @Test
    fun staleSessionReturnsReconnectWithoutNetwork() = runTest {
        cache.save(ACCOUNT, false, build("b-1"), t0)
        session.value = WatchSessionState.Stale

        assertEquals(SurfaceData(null, null, SurfaceStatus.Reconnect), refresher.refresh())
        assertTrue(source.calls.isEmpty())
        assertNull(cache.load())
    }

    @Test
    fun restoringSessionThatNeverSettlesIsUnknownSoItShowsNoDataAndKeepsTheCache() = runTest {
        cache.save(ACCOUNT, false, build("b-1"), t0)
        session.value = WatchSessionState.Restoring

        val data = refresher.refresh()

        assertEquals(SurfaceData(null, null, SurfaceStatus.Stale), data)
        assertEquals(NO_DATA_TITLE, complicationForSurface(data, t0).title)
        assertEquals(NO_DATA_TITLE, tileForSurface(data, t0).workerName)
        assertTrue(source.calls.isEmpty())
        assertEquals("b-1", cache.load()!!.build.buildUuid)
    }

    private suspend fun assertSwitchDuringCallReconnects(to: WatchSessionState.Connected) {
        cache.save(ACCOUNT, false, build("cached-a"), t0)
        val switching = object : com.tomasps.virga.wear.builds.WearBuilds by source {
            override suspend fun latest(accountId: String): ApiResult<BuildSummary?> {
                session.value = to
                return ApiResult.Success(build("from-a"))
            }
        }

        val data = BuildSurfaceRefresher(switching, session, cache, warn = warnings::add) { now }.refresh()

        assertEquals(SurfaceData(null, null, SurfaceStatus.Reconnect), data)
        assertNull("the old session's data is cleared, not served", cache.load())
    }

    @Test
    fun accountSwitchDuringTheCallReturnsReconnectAndNoData() = runTest {
        assertSwitchDuringCallReconnects(WatchSessionState.Connected("acc-2", isDemo = false))
    }

    @Test
    fun demoFlipOnTheSameAccountDuringTheCallReturnsReconnectAndNoData() = runTest {
        assertSwitchDuringCallReconnects(WatchSessionState.Connected(ACCOUNT, isDemo = true))
    }

    @Test
    fun demoToLiveOnTheSameAccountIsACacheMiss() = runTest {
        cache.save(ACCOUNT, true, build("demo-build"), t0)
        source.recent = failure(ApiError.Server(500))

        assertEquals(SurfaceData(null, null, SurfaceStatus.Stale), refresher.refresh())
        assertNull(cache.load())
    }

    @Test
    fun concurrentRefreshesRunOneFetchEach() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var running = 0
        var scans = 0
        var maxRunning = 0
        val slow = object : com.tomasps.virga.wear.builds.WearBuilds by source {
            override suspend fun latest(accountId: String): ApiResult<BuildSummary?> {
                running++
                scans++
                maxRunning = maxOf(maxRunning, running)
                gate.await()
                running--
                return ApiResult.Success(build("b-1"))
            }
        }
        val serialized = BuildSurfaceRefresher(slow, session, cache, warn = warnings::add) { now }

        val first = async { serialized.refresh() }
        val second = async { serialized.refresh() }
        yield()
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, maxRunning)
        // A plain Mutex runs the callers one after the other: two scans, never overlapping (no shared in-flight result).
        assertEquals(2, scans)
    }

    @Test
    fun cacheWriteFailureStillReturnsTheFetchedBuildAsFresh() = runTest {
        val broken = LatestBuildCache(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "broken.preferences_pb") },
        )
        folder.root.resolve("broken.preferences_pb").mkdirs() // a directory where the file should be: writes fail
        succeedWith(build("b-1"))

        val data = BuildSurfaceRefresher(source, session, broken, warn = warnings::add) { now }.refresh()

        assertEquals(SurfaceStatus.Fresh, data.status)
        assertEquals("b-1", data.latest!!.buildUuid)
        assertTrue(warnings.isNotEmpty())
    }

    @Test
    fun unauthorizedWipesSessionAndReturnsReconnect() = runTest {
        cache.save(ACCOUNT, false, build("b-1"), t0)
        val wiping = object : com.tomasps.virga.wear.builds.WearBuilds by source {
            override suspend fun latest(accountId: String): ApiResult<BuildSummary?> {
                session.value = WatchSessionState.NotConnected // what WearBuildsSource's 401 handling does
                return failure(ApiError.Unauthorized())
            }
        }

        val data = BuildSurfaceRefresher(wiping, session, cache, warn = warnings::add) { now }.refresh()

        assertEquals(SurfaceData(null, null, SurfaceStatus.Reconnect), data)
        assertNull(cache.load())
    }
}
