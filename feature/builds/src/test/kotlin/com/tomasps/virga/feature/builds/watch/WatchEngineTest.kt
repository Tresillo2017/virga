package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.RateBudget
import com.tomasps.virga.feature.builds.testing.FakeWatchStore
import com.tomasps.virga.feature.builds.testing.ScriptedFetcher
import com.tomasps.virga.feature.builds.testing.TEST_ACCOUNT
import com.tomasps.virga.feature.builds.testing.TEST_BUILD
import com.tomasps.virga.feature.builds.testing.TEST_SCRIPT
import com.tomasps.virga.feature.builds.testing.ok
import com.tomasps.virga.feature.builds.testing.assertIs
import com.tomasps.virga.feature.builds.testing.rateLimited
import com.tomasps.virga.feature.builds.testing.record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchEngineTest {

    private var now = 0L
    private var budget: RateBudget? = null
    private val events = mutableListOf<WatchEvent>()

    private fun engine(fetcher: ScriptedFetcher, registry: WatchRegistry) = WatchEngine(
        registry = registry,
        fetcher = fetcher,
        budget = { budget },
        onEvent = { events += it },
    )

    private fun registryWithBuild(scope: kotlinx.coroutines.CoroutineScope) =
        WatchRegistry(FakeWatchStore(), scope, clockMillis = { now }).also {
            it.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)
        }

    @Test
    fun `a running build is reported as progress and polled again after the interval`() = runTest {
        val fetcher = ScriptedFetcher(mapOf(TEST_BUILD to listOf(ok(record(status = BuildStatus.RUNNING)))))
        val registry = registryWithBuild(backgroundScope)
        val engine = engine(fetcher, registry)

        val delay = engine.tick(nowMillis = 0L)

        assertIs<WatchEvent.Progress>(events.single())
        assertEquals(WatchPolicy.BASE_INTERVAL_MS, delay)
        assertEquals(1, fetcher.callsFor(TEST_BUILD))
    }

    @Test
    fun `nothing is polled before the next poll is due`() = runTest {
        val fetcher = ScriptedFetcher(mapOf(TEST_BUILD to listOf(ok(record(status = BuildStatus.RUNNING)))))
        val registry = registryWithBuild(backgroundScope)
        val engine = engine(fetcher, registry)
        engine.tick(0L)

        engine.tick(WatchPolicy.BASE_INTERVAL_MS - 1)

        assertEquals(1, fetcher.callsFor(TEST_BUILD))
    }

    @Test
    fun `a stopped build with an outcome finishes once and leaves the registry`() = runTest {
        val done = record(status = BuildStatus.STOPPED, outcome = BuildOutcome.SUCCESS)
        val fetcher = ScriptedFetcher(mapOf(TEST_BUILD to listOf(ok(done))))
        val registry = registryWithBuild(backgroundScope)

        val delay = engine(fetcher, registry).tick(0L)

        assertIs<WatchEvent.Finished>(events.single())
        assertTrue(registry.snapshot().isEmpty())
        assertNull(delay)
    }

    @Test
    fun `a skipped build counts as finished, not as an error`() = runTest {
        val skipped = record(status = BuildStatus.STOPPED, outcome = BuildOutcome.SKIPPED)
        val registry = registryWithBuild(backgroundScope)

        engine(ScriptedFetcher(mapOf(TEST_BUILD to listOf(ok(skipped)))), registry).tick(0L)

        assertIs<WatchEvent.Finished>(events.single())
    }

    @Test
    fun `a rate limit waits for Retry-After and does not report progress`() = runTest {
        val fetcher = ScriptedFetcher(mapOf(TEST_BUILD to listOf(rateLimited(30_000L), ok(record()))))
        val registry = registryWithBuild(backgroundScope)
        val engine = engine(fetcher, registry)

        val delay = engine.tick(0L)

        assertTrue(events.isEmpty())
        assertEquals(30_000L, delay)
        assertEquals(30_000L, registry.snapshot().getValue(TEST_BUILD).nextPollAtMillis)
    }

    @Test
    fun `transient errors back off, doubling the wait each time`() = runTest {
        val local = ApiResult.Failure(ApiError.Local("network hiccup"))
        val fetcher = ScriptedFetcher(mapOf(TEST_BUILD to listOf(local)))
        val registry = registryWithBuild(backgroundScope)
        val engine = engine(fetcher, registry)

        engine.tick(0L)
        assertEquals(WatchPolicy.MIN_BACKOFF_MS, registry.snapshot().getValue(TEST_BUILD).backoffMillis)

        now = WatchPolicy.MIN_BACKOFF_MS
        engine.tick(now)
        assertEquals(WatchPolicy.MIN_BACKOFF_MS * 2, registry.snapshot().getValue(TEST_BUILD).backoffMillis)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a missing build is reported as failed and removed`() = runTest {
        val fetcher = ScriptedFetcher(mapOf(TEST_BUILD to listOf(ApiResult.Failure(ApiError.NotFound()))))
        val registry = registryWithBuild(backgroundScope)

        engine(fetcher, registry).tick(0L)

        assertIs<WatchEvent.Failed>(events.single())
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun `a watch past the two hour cap expires without another read`() = runTest {
        val fetcher = ScriptedFetcher(mapOf(TEST_BUILD to listOf(ok(record()))))
        val registry = registryWithBuild(backgroundScope)

        engine(fetcher, registry).tick(WatchPolicy.MAX_WATCH_MS)

        assertIs<WatchEvent.Expired>(events.single())
        assertEquals(0, fetcher.callsFor(TEST_BUILD))
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun `several watched builds are each polled once in the same tick`() = runTest {
        val registry = WatchRegistry(FakeWatchStore(), backgroundScope, clockMillis = { 0L })
        registry.add(TEST_ACCOUNT, TEST_SCRIPT, "a")
        registry.add(TEST_ACCOUNT, TEST_SCRIPT, "b")
        val fetcher = ScriptedFetcher(
            mapOf(
                "a" to listOf(ok(record(uuid = "a"))),
                "b" to listOf(ok(record(uuid = "b", status = BuildStatus.STOPPED, outcome = BuildOutcome.FAIL))),
            ),
        )

        engine(fetcher, registry).tick(0L)

        assertEquals(2, events.size)
        assertIs<WatchEvent.Progress>(events[0])
        assertIs<WatchEvent.Finished>(events[1])
    }

    @Test
    fun `a low shared budget slows the next poll to fifteen seconds`() = runTest {
        val low = RateBudget(restRemaining = 10, restCapacity = 150, graphQlRemaining = 40, graphQlCapacity = 40, blockedForMillis = 0)
        budget = low
        val fetcher = ScriptedFetcher(mapOf(TEST_BUILD to listOf(ok(record()))))

        val delay = engine(fetcher, registryWithBuild(backgroundScope)).tick(0L)

        assertEquals(WatchPolicy.LOW_BUDGET_INTERVAL_MS, delay)
    }

    @Test
    fun `two builds watched at once - the finished one leaves and the running one keeps its own polling`() = runTest {
        val registry = WatchRegistry(FakeWatchStore(), backgroundScope, clockMillis = { now })
        registry.add(TEST_ACCOUNT, TEST_SCRIPT, "a")
        registry.add(TEST_ACCOUNT, TEST_SCRIPT, "b")
        val fetcher = ScriptedFetcher(
            mapOf(
                "a" to listOf(
                    ok(record(uuid = "a", status = BuildStatus.RUNNING)),
                    ok(record(uuid = "a", status = BuildStatus.STOPPED, outcome = BuildOutcome.SUCCESS)),
                ),
                "b" to listOf(ok(record(uuid = "b", status = BuildStatus.RUNNING))),
            ),
        )
        val engine = engine(fetcher, registry)

        engine.tick(0L)
        now = WatchPolicy.BASE_INTERVAL_MS
        val delay = engine.tick(now)

        val finished = events.filterIsInstance<WatchEvent.Finished>().single()
        assertEquals("a", finished.build.buildUuid)
        assertEquals(setOf("b"), registry.snapshot().keys)
        assertEquals(WatchPolicy.BASE_INTERVAL_MS, delay)
        assertEquals(2, fetcher.callsFor("b"))
    }
}
