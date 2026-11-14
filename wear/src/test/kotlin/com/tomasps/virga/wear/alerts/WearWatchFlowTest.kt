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
import org.junit.Assert.assertFalse
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
    fun fetcherDelegatesToWearBuildsRecord() = runTest {
        val builds = FakeWearBuilds()
        builds.record = ApiResult.Success(PASSED)

        val result = WearBuildFetcher(builds).fetch(ACCOUNT, "api", "b-1")

        assertEquals(ApiResult.Success(PASSED), result)
        assertEquals(listOf("record:$ACCOUNT:api/b-1"), builds.calls)
    }

    @Test
    fun startArrivingBeforeTheStopIsReadDoesNotStopTheService() = runTest(UnconfinedTestDispatcher()) {
        val registry = registry()
        var latestStartId = 1
        var stops = 0
        val gate = ServiceStopGate(registry, { latestStartId }) { id -> stops++; id == latestStartId }

        // The last watch finished (registry empty); then a Watch tap bumps the start id and adds a watch.
        latestStartId = 2
        registry.add(ACCOUNT, "api", "b-2")

        assertFalse(gate.tryStop())
        assertEquals("Android was never asked to stop", 0, stops)
    }

    @Test
    fun stopIsRefusedWhenAStartArrivesAfterTheIdWasRead() = runTest(UnconfinedTestDispatcher()) {
        val registry = registry()
        var latestStartId = 1
        val gate = ServiceStopGate(registry, { latestStartId }) { id -> id == latestStartId }
        val staleRead = ServiceStopGate(registry, { latestStartId.also { latestStartId++ } }) { id -> id == latestStartId }

        assertTrue(gate.tryStop())
        assertFalse("a newer start id makes Android refuse", staleRead.tryStop())
    }

    @Test
    fun aWatchStartedBetweenEmptyAndStopIsPolledNotAbandoned() = runTest(UnconfinedTestDispatcher()) {
        val h = harness(onFetch = { ApiResult.Success(PASSED) })
        var reads = 0
        // The start arrives right as the gate reads the id: after the runner returned, before the registry check.
        val gate = ServiceStopGate(h.registry, {
            if (reads++ == 0) h.registry.add(ACCOUNT, "api", "b-2")
            1
        }) { true }
        var retrySleeps = 0
        val loop = WatchServiceLoop(h.runner, gate) { retrySleeps++ }

        loop.run()

        assertEquals("the new watch was polled", listOf("b-2"), h.fetches)
        assertEquals(1, retrySleeps)
        assertTrue(h.registry.snapshot().isEmpty())
    }

    @Test
    fun crashGuardLogsTheClassNameOnlyPausesWatchesAndStops() = runTest(UnconfinedTestDispatcher()) {
        val registry = registry()
        registry.add(ACCOUNT, "api", "b-1")
        val logs = mutableListOf<String>()
        var stopped = false
        val guard = WatchCrashGuard(registry, { logs += it }) { stopped = true }

        guard.onFailure(IllegalStateException("secret-token-123"))

        assertTrue(registry.snapshot().isEmpty())
        assertTrue(stopped)
        assertTrue(logs.single().contains("IllegalStateException"))
        assertFalse(logs.single().contains("secret"))
    }

    @Test
    fun refusedServiceStartRemovesTheWatchAndReportsIt() = runTest(UnconfinedTestDispatcher()) {
        val registry = registry()
        val starter = RegistryWatchStarter(registry) { throw IllegalStateException("not allowed") }

        val started = starter.start(ACCOUNT, "api", "b-1")

        assertFalse(started)
        assertTrue("no active entry nobody polls", registry.snapshot().isEmpty())
    }
}
