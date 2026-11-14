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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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
    private val session = MutableStateFlow<WatchSessionState>(CONNECTED)
    private val source = FakeWearBuilds()
    private val cache by lazy {
        LatestBuildCache(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "c.preferences_pb") })
    }
    private val refresher by lazy { BuildSurfaceRefresher(source, session, cache) { now } }

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
        assertEquals(listOf("recent:$ACCOUNT"), source.calls)
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
        succeedWith()
        now = t1

        assertEquals(SurfaceData(null, t1, SurfaceStatus.Fresh), refresher.refresh())
        assertNull(cache.load())
    }

    @Test
    fun cachedBuildOfAnotherAccountIsNeverShown() = runTest {
        cache.save("acc-other", build("b-other"), t0)
        source.recent = failure(ApiError.Server(500))

        assertEquals(SurfaceData(null, null, SurfaceStatus.Stale), refresher.refresh())
    }

    @Test
    fun notConnectedSessionReturnsNotConnectedWithoutNetwork() = runTest {
        cache.save(ACCOUNT, build("b-1"), t0)
        session.value = WatchSessionState.NotConnected

        assertEquals(SurfaceData(null, null, SurfaceStatus.NotConnected), refresher.refresh())
        assertTrue(source.calls.isEmpty())
        assertNull(cache.load())
    }

    @Test
    fun staleSessionReturnsReconnectWithoutNetwork() = runTest {
        cache.save(ACCOUNT, build("b-1"), t0)
        session.value = WatchSessionState.Stale

        assertEquals(SurfaceData(null, null, SurfaceStatus.Reconnect), refresher.refresh())
        assertTrue(source.calls.isEmpty())
        assertNull(cache.load())
    }

    @Test
    fun restoringSessionThatNeverSettlesReturnsNotConnectedWithoutNetwork() = runTest {
        session.value = WatchSessionState.Restoring

        assertEquals(SurfaceStatus.NotConnected, refresher.refresh().status)
        assertTrue(source.calls.isEmpty())
    }

    @Test
    fun unauthorizedWipesSessionAndReturnsReconnect() = runTest {
        cache.save(ACCOUNT, build("b-1"), t0)
        val wiping = object : com.tomasps.virga.wear.builds.WearBuilds by source {
            override suspend fun recent(accountId: String): ApiResult<List<BuildSummary>> {
                session.value = WatchSessionState.NotConnected // what WearBuildsSource's 401 handling does
                return failure(ApiError.Unauthorized())
            }
        }

        val data = BuildSurfaceRefresher(wiping, session, cache) { now }.refresh()

        assertEquals(SurfaceData(null, null, SurfaceStatus.Reconnect), data)
        assertNull(cache.load())
    }
}
