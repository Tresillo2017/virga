package com.tomasps.virga.wear.summary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.RateLimiter
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.wear.session.WatchSessionState
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Instant

private const val NANOS_PER_MILLI = 1_000_000L
private const val ACCOUNT = "acc-1"

/** The refresher over a real [SummarySource] and a mock Cloudflare API, so error paths are the real ones. */
class SummaryRefresherTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val t0 = Instant.parse("2026-11-14T09:00:00Z")
    private val t1 = Instant.parse("2026-11-14T12:00:00Z")
    private val connected = WatchSessionState.Connected(ACCOUNT, isDemo = false)
    private val session = MutableStateFlow<WatchSessionState>(connected)
    private val cache by lazy {
        SummaryCache(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "c.preferences_pb") })
    }
    private val cachedData = SummaryData(100, 5)

    @After
    fun tearDown() = scope.cancel()

    private fun fastRateLimiter(): RateLimiter {
        var nanos = 0L
        return RateLimiter(nowNanos = { nanos }, delayMillis = { nanos += it * NANOS_PER_MILLI })
    }

    private fun ok(requests: Long, errors: Long) = DemoReply.graphQl(
        Json.parseToJsonElement(
            """{"viewer":{"accounts":[{"totals":[{"sum":{"requests":$requests,"errors":$errors}}]}]}}""",
        ),
    )

    private fun api(handler: suspend () -> DemoReply): CloudflareApi = mockCloudflareApi(rateLimiter = fastRateLimiter()) {
        graphQl(SUMMARY_OPERATION) { handler() }
    }

    private fun refresher(
        api: CloudflareApi,
        summaryCache: SummaryCache = cache,
        onWipe: () -> Unit = {},
        warn: (String) -> Unit = {},
    ) = SummaryRefresher(
        SummarySource(api.graphQl, onUnauthorized = { onWipe() }, hasToken = { true }, warn = {}),
        session,
        summaryCache,
        warn,
    ) { t1 }

    @Test
    fun freshFetchIsReturnedAndCached() = runTest {
        val surface = refresher(api { ok(1_200, 34) }).refresh()

        assertEquals(SummarySurface(SummaryData(1_200, 34), t1, SummaryStatus.Fresh), surface)
        assertEquals(SummaryData(1_200, 34), cache.load()!!.data)
    }

    @Test
    fun genuineZeroIsAFreshZeroNotNoData() = runTest {
        cache.save(ACCOUNT, false, cachedData, t0)

        val surface = refresher(api { ok(0, 0) }).refresh()

        assertEquals(SummarySurface(SummaryData(0, 0), t1, SummaryStatus.Fresh), surface)
    }

    @Test
    fun malformedOkResponsesKeepTheCachedNumbersAsStaleAndDoNotOverwriteTheCache() = runTest {
        cache.save(ACCOUNT, false, cachedData, t0)
        val bodies = listOf(
            """{"viewer":{"accounts":[]}}""",
            """{"viewer":{"accounts":[{}]}}""",
            """{"viewer":{"accounts":[{"totals":[{"sum":null}]}]}}""",
            """{"viewer":{"accounts":[{"totals":[{"sum":{"requests":"x","errors":null}}]}]}}""",
        )

        bodies.forEach { body ->
            val surface = refresher(api { DemoReply.graphQl(Json.parseToJsonElement(body)) }).refresh()

            assertEquals(SummarySurface(cachedData, t0, SummaryStatus.Stale), surface)
            assertEquals(cachedData, cache.load()!!.data)
        }
    }

    @Test
    fun genuineNoTrafficResponseIsFreshZeroAndReplacesTheCache() = runTest {
        cache.save(ACCOUNT, false, cachedData, t0)
        val noTraffic = DemoReply.graphQl(Json.parseToJsonElement("""{"viewer":{"accounts":[{"totals":[]}]}}"""))

        val surface = refresher(api { noTraffic }).refresh()

        assertEquals(SummarySurface(SummaryData(0, 0), t1, SummaryStatus.Fresh), surface)
        assertEquals(SummaryData(0, 0), cache.load()!!.data)
    }

    @Test
    fun rateLimitedReturnsTheLastValueAsStaleWithItsFetchTimeAndKeepsTheCache() = runTest {
        cache.save(ACCOUNT, false, cachedData, t0)

        val surface = refresher(api { DemoReply.failure(HttpStatusCode.TooManyRequests, 971, "rate limited") }).refresh()

        assertEquals(SummarySurface(cachedData, t0, SummaryStatus.Stale), surface)
        assertNotNull(cache.load())
    }

    @Test
    fun serverNetworkAndDecodeFailuresAllFallBackToTheCache() = runTest {
        cache.save(ACCOUNT, false, cachedData, t0)
        val failures = listOf<suspend () -> DemoReply>(
            { DemoReply.failure(HttpStatusCode.ServiceUnavailable, 1, "down") },
            { throw IOException("offline") },
            { DemoReply.graphQl(Json.parseToJsonElement("{}")) },
        )

        failures.forEach { failure ->
            assertEquals(SummarySurface(cachedData, t0, SummaryStatus.Stale), refresher(api(failure)).refresh())
        }
    }

    @Test
    fun failureWithAnEmptyCacheIsStaleWithNoData() = runTest {
        val surface = refresher(api { DemoReply.failure(HttpStatusCode.ServiceUnavailable, 1, "down") }).refresh()

        assertEquals(SummarySurface(null, null, SummaryStatus.Stale), surface)
    }

    @Test
    fun cacheOfAnotherAccountIsAMissAndIsCleared() = runTest {
        cache.save("other", false, cachedData, t0)

        val surface = refresher(api { DemoReply.failure(HttpStatusCode.ServiceUnavailable, 1, "down") }).refresh()

        assertEquals(SummarySurface(null, null, SummaryStatus.Stale), surface)
        assertNull(cache.load())
    }

    @Test
    fun cacheOfTheOtherDemoModeIsAMissAndIsCleared() = runTest {
        cache.save(ACCOUNT, true, cachedData, t0)

        val surface = refresher(api { DemoReply.failure(HttpStatusCode.ServiceUnavailable, 1, "down") }).refresh()

        assertEquals(SummarySurface(null, null, SummaryStatus.Stale), surface)
        assertNull(cache.load())
    }

    @Test
    fun missingAnalyticsAccessIsShownInsteadOfAStaleValueAndDropsTheCache() = runTest {
        cache.save(ACCOUNT, false, cachedData, t0)

        val surface = refresher(api { DemoReply.graphQlErrors("not authorized for account analytics") }).refresh()

        assertEquals(SummarySurface(null, null, SummaryStatus.MissingAnalyticsAccess), surface)
        assertNull(cache.load())
    }

    @Test
    fun missingAnalyticsAccessDoesNotWipeTheSession() = runTest {
        var wiped = 0

        refresher(api { DemoReply.graphQlErrors("not authorized") }, onWipe = { wiped++ }).refresh()

        assertEquals(0, wiped)
        assertEquals(connected, session.value)
    }

    @Test
    fun rejectedTokenWipesOnceAndReturnsReconnectWithoutTheCache() = runTest {
        cache.save(ACCOUNT, false, cachedData, t0)

        val surface = refresher(
            api { DemoReply.failure(HttpStatusCode.Unauthorized, 9109, "bad") },
            onWipe = { session.value = WatchSessionState.NotConnected },
        ).refresh()

        assertEquals(SummarySurface(null, null, SummaryStatus.Reconnect), surface)
        assertNull(cache.load())
    }

    @Test
    fun accountSwitchDuringTheCallNeverShowsTheOtherAccountsNumbers() = runTest {
        val surface = refresher(
            api {
                session.value = WatchSessionState.Connected("acc-2", isDemo = false)
                ok(999, 9)
            },
        ).refresh()

        assertEquals(SummarySurface(null, null, SummaryStatus.Reconnect), surface)
        assertNull(cache.load())
    }

    @Test
    fun demoSwitchDuringTheCallIsAlsoAChange() = runTest {
        val surface = refresher(
            api {
                session.value = WatchSessionState.Connected(ACCOUNT, isDemo = true)
                ok(999, 9)
            },
        ).refresh()

        assertEquals(SummaryStatus.Reconnect, surface.status)
    }

    @Test
    fun notConnectedNeverHitsTheNetworkAndClearsTheCache() = runTest {
        cache.save(ACCOUNT, false, cachedData, t0)
        session.value = WatchSessionState.NotConnected
        var calls = 0

        val surface = refresher(api { calls++; ok(1, 1) }).refresh()

        assertEquals(SummarySurface(null, null, SummaryStatus.NotConnected), surface)
        assertEquals(0, calls)
        assertNull(cache.load())
    }

    @Test
    fun staleSessionNeverHitsTheNetworkAndMapsToReconnect() = runTest {
        session.value = WatchSessionState.Stale
        var calls = 0

        val surface = refresher(api { calls++; ok(1, 1) }).refresh()

        assertEquals(SummarySurface(null, null, SummaryStatus.Reconnect), surface)
        assertEquals(0, calls)
    }

    @Test
    fun restoringThatNeverSettlesIsNoDataAndKeepsTheCache() = runTest {
        cache.save(ACCOUNT, false, cachedData, t0)
        session.value = WatchSessionState.Restoring
        var calls = 0

        val surface = refresher(api { calls++; ok(1, 1) }).refresh()

        assertEquals(SummarySurface(null, null, SummaryStatus.Stale), surface)
        assertEquals(0, calls)
        assertNotNull(cache.load())
    }

    @Test
    fun aCacheWriteFailureStillReturnsTheFetchedNumbersAndLogsOnlyTheClassName() = runTest {
        val warnings = mutableListOf<String>()
        val failing = SummaryCache(object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("secret path /data/acc-1")
        })

        val surface = refresher(api { ok(7, 1) }, failing, warn = { warnings += it }).refresh()

        assertEquals(SummarySurface(SummaryData(7, 1), t1, SummaryStatus.Fresh), surface)
        assertEquals(1, warnings.size)
        assertEquals(false, warnings.single().contains("secret"))
        assertEquals(true, warnings.single().contains("IOException"))
    }

    @Test
    fun concurrentRefreshesTakeTurnsInsteadOfDoublingTheFanOut() = runTest {
        var active = 0
        var peak = 0
        val gate = CompletableDeferred<Unit>()
        val refresher = refresher(
            api {
                active++
                peak = maxOf(peak, active)
                gate.await()
                active--
                ok(1, 0)
            },
        )

        val both = listOf(async { refresher.refresh() }, async { refresher.refresh() })
        gate.complete(Unit)
        both.awaitAll()

        assertEquals(1, peak)
    }
}
