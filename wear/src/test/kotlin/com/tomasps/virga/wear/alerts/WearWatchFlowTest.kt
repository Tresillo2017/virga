package com.tomasps.virga.wear.alerts

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.watch.BuildFetcher
import com.tomasps.virga.feature.builds.watch.WatchEntry
import com.tomasps.virga.feature.builds.watch.WatchEvent
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import com.tomasps.virga.feature.builds.watch.WatchStore
import com.tomasps.virga.wear.session.WatchSessionState
import com.tomasps.virga.wear.ui.FakeWearBuilds
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val NOW = 1_000_000L

private class MemoryWatchStore : WatchStore {
    override suspend fun load(): List<WatchEntry> = emptyList()
    override suspend fun save(entries: List<WatchEntry>) = Unit
}

private fun record(id: String = "b-1", status: BuildStatus, outcome: BuildOutcome? = null) = BuildRecord(
    buildUuid = id,
    scriptName = "api",
    status = status,
    outcome = outcome,
    createdOn = Instant.parse("2026-11-14T10:00:00Z"),
)

private val RUNNING = record(status = BuildStatus.RUNNING)
private val PASSED = record(status = BuildStatus.STOPPED, outcome = BuildOutcome.SUCCESS)

@OptIn(ExperimentalCoroutinesApi::class)
class WearWatchFlowTest {

    private fun TestScope.registry() = WatchRegistry(MemoryWatchStore(), backgroundScope) { NOW }

    private class Harness(
        val registry: WatchRegistry,
        val session: MutableStateFlow<WatchSessionState>,
        val fetches: MutableList<String>,
        val events: MutableList<WatchEvent>,
        val sleeps: MutableList<Long>,
        val runner: WearWatchRunner,
    )

    private fun TestScope.harness(
        session: WatchSessionState = WatchSessionState.Connected(ACCOUNT, isDemo = false),
        onFetch: (String) -> ApiResult<BuildRecord>,
        onSleep: (MutableStateFlow<WatchSessionState>) -> Unit = {},
    ): Harness {
        val registry = registry()
        val state = MutableStateFlow(session)
        val fetches = mutableListOf<String>()
        val events = mutableListOf<WatchEvent>()
        val sleeps = mutableListOf<Long>()
        val runner = WearWatchRunner(
            registry = registry,
            session = state,
            fetcher = BuildFetcher { _, _, uuid -> fetches += uuid; onFetch(uuid) },
            budget = { null },
            onEvent = { events += it },
            clockMillis = { NOW + sleeps.sum() },
            sleep = { sleeps += it; onSleep(state) },
        )
        return Harness(registry, state, fetches, events, sleeps, runner)
    }

    @Test
    fun serviceStopsWhenRegistryEmpties() = runTest(UnconfinedTestDispatcher()) {
        var polls = 0
        val h = harness(onFetch = { if (++polls < 3) ApiResult.Success(RUNNING) else ApiResult.Success(PASSED) })
        h.registry.add(ACCOUNT, "api", "b-1")

        val stop = h.runner.run()

        assertEquals(WatchStop.RegistryEmpty, stop)
        assertTrue(h.registry.snapshot().isEmpty())
        assertEquals(3, h.fetches.size)
        assertTrue(h.events.last() is WatchEvent.Finished)
    }

    @Test
    fun serviceStopsWhenSessionBecomesStale() = runTest(UnconfinedTestDispatcher()) {
        val h = harness(
            onFetch = { ApiResult.Success(RUNNING) },
            onSleep = { it.value = WatchSessionState.Stale },
        )
        h.registry.add(ACCOUNT, "api", "b-1")

        val stop = h.runner.run()

        assertEquals(WatchStop.SessionEnded, stop)
        assertEquals("polled once, then never with a stale credential", 1, h.fetches.size)
        assertTrue("watches are cleared", h.registry.snapshot().isEmpty())
    }

    @Test
    fun runnerStopsAtOnceWhenSessionIsNotConnected() = runTest(UnconfinedTestDispatcher()) {
        val h = harness(session = WatchSessionState.NotConnected, onFetch = { ApiResult.Success(RUNNING) })
        h.registry.add(ACCOUNT, "api", "b-1")

        assertEquals(WatchStop.SessionEnded, h.runner.run())
        assertTrue(h.fetches.isEmpty())
        assertTrue(h.registry.snapshot().isEmpty())
    }

    @Test
    fun accountChangeDropsTheOtherAccountsWatches() = runTest(UnconfinedTestDispatcher()) {
        val h = harness(
            onFetch = { ApiResult.Success(RUNNING) },
            onSleep = { it.value = WatchSessionState.Connected("acc-2", isDemo = false) },
        )
        h.registry.add(ACCOUNT, "api", "b-1")

        assertEquals(WatchStop.RegistryEmpty, h.runner.run())
        assertEquals(1, h.fetches.size)
    }

    @Test
    fun failedPollEndsTheWatchWithAFailedEvent() = runTest(UnconfinedTestDispatcher()) {
        val h = harness(onFetch = { ApiResult.Failure(ApiError.NotFound()) })
        h.registry.add(ACCOUNT, "api", "b-1")

        assertEquals(WatchStop.RegistryEmpty, h.runner.run())
        assertTrue(h.events.single() is WatchEvent.Failed)
    }

    @Test
    fun startWatchingAlreadyWatchedBuildDoesNotDuplicate() = runTest(UnconfinedTestDispatcher()) {
        val registry = registry()
        var serviceStarts = 0
        val starter = RegistryWatchStarter(registry) { serviceStarts++ }

        starter.start(ACCOUNT, "api", "b-1")
        starter.start(ACCOUNT, "api", "b-1")

        assertEquals(1, registry.snapshot().size)
        assertEquals("the service is started once", 1, serviceStarts)
        starter.stop("api", "b-1")
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun fetcherReadsTheBuildThroughWearBuildsSoA401TakesTheSharedWipePath() = runTest {
        val builds = FakeWearBuilds()
        builds.record = ApiResult.Success(PASSED)

        val result = WearBuildFetcher(builds).fetch(ACCOUNT, "api", "b-1")

        assertEquals(ApiResult.Success(PASSED), result)
        assertEquals(listOf("record:$ACCOUNT:api/b-1"), builds.calls)
    }
}
