package com.tomasps.virga.feature.builds.gateway

import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.testing.FakeWatchStore
import com.tomasps.virga.feature.builds.testing.TEST_ACCOUNT
import com.tomasps.virga.feature.builds.testing.TEST_BUILD
import com.tomasps.virga.feature.builds.testing.TEST_SCRIPT
import com.tomasps.virga.feature.builds.watch.SummaryDismissal
import com.tomasps.virga.feature.builds.watch.WatchEntry
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import com.tomasps.virga.feature.builds.watch.WatchServiceStarter
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val OTHER_ACCOUNT = "other-account"
private const val LIVE_BUILD = "b-live"

private class FakeSession(initial: SessionState) : SessionRepository {
    val current = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = current

    override suspend fun restore() = Unit
    override suspend fun signInWithToken(token: String): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun enterDemo(): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun selectAccount(accountId: String) = Unit
    override suspend fun signOut() = Unit
}

private class RecordingStarter : WatchServiceStarter {
    var starts = 0
    override fun start() {
        starts++
    }
}

private fun signedIn(selected: String?) = SessionState.SignedIn(accounts = emptyList(), selectedAccountId = selected, isDemo = false)

private fun summary() = BuildSummary(
    buildUuid = TEST_BUILD,
    scriptName = TEST_SCRIPT,
    status = BuildStatus.RUNNING,
    createdOn = Instant.EPOCH,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBuildsGatewayTest {

    /** The gateway's background scope runs on the test scheduler and is cancelled after each test. */
    private fun runGatewayTest(body: suspend TestScope.(GatewayFixture) -> Unit) = runTest {
        val fixture = GatewayFixture(testScheduler)
        try {
            body(fixture)
        } finally {
            fixture.scope.cancel()
        }
    }

    private class GatewayFixture(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) {
        val scope = CoroutineScope(StandardTestDispatcher(scheduler) + Job())
        val starter = RecordingStarter()
        var store = FakeWatchStore()
        val session = FakeSession(signedIn(TEST_ACCOUNT))
        lateinit var registry: WatchRegistry
        lateinit var gateway: DefaultBuildsGateway

        fun build(
            savedEntries: List<WatchEntry> = emptyList(),
            repository: BuildsRepository = BuildsRepository(mockCloudflareApi { }),
        ) {
            store = FakeWatchStore(savedEntries)
            registry = WatchRegistry(store, scope, clockMillis = { 0L })
            gateway = DefaultBuildsGateway(
                repository = repository,
                registry = registry,
                starter = starter,
                dismissal = SummaryDismissal(),
                session = session,
                scope = scope,
            )
        }
    }

    @Test
    fun `saved watches are not shown as watched until the user taps Watch on them`() = runGatewayTest { fixture ->
        fixture.build(savedEntries = listOf(WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD, 1L)))
        advanceUntilIdle()

        assertEquals(emptySet<String>(), fixture.gateway.watchedBuildIds().first())
        assertEquals(0, fixture.starter.starts)

        fixture.gateway.startWatching(TEST_ACCOUNT, summary())
        advanceUntilIdle()

        assertEquals(setOf(TEST_BUILD), fixture.gateway.watchedBuildIds().first())
        assertEquals(1, fixture.starter.starts)
    }

    @Test
    fun `sign-out drops saved and active watches`() = runGatewayTest { fixture ->
        fixture.build(savedEntries = listOf(WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, "saved", 1L)))
        advanceUntilIdle()
        fixture.gateway.startWatching(TEST_ACCOUNT, summary())
        advanceUntilIdle()

        fixture.session.current.value = SessionState.SignedOut
        advanceUntilIdle()

        assertEquals(emptySet<String>(), fixture.gateway.watchedBuildIds().first())
        assertTrue(fixture.store.saved.isEmpty())
    }

    @Test
    fun `switching accounts drops the watches of the previous account`() = runGatewayTest { fixture ->
        fixture.build(savedEntries = listOf(WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, "saved", 1L)))
        advanceUntilIdle()
        fixture.gateway.startWatching(TEST_ACCOUNT, summary())
        advanceUntilIdle()

        fixture.session.current.value = signedIn(OTHER_ACCOUNT)
        advanceUntilIdle()

        assertEquals(emptySet<String>(), fixture.gateway.watchedBuildIds().first())
        assertTrue(fixture.store.saved.isEmpty())
    }

    @Test
    fun `active builds still list the live build when one Worker's builds cannot be read`() = runGatewayTest { fixture ->
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") {
                DemoReply.ok(
                    buildJsonArray {
                        add(buildJsonObject { put("id", "broken"); put("tag", "T1") })
                        add(buildJsonObject { put("id", "healthy"); put("tag", "T2") })
                    },
                )
            }
            get("/accounts/{accountId}/builds/workers/T1/builds") {
                DemoReply.failure(HttpStatusCode.InternalServerError, 500, "boom")
            }
            get("/accounts/{accountId}/builds/workers/T2/builds") {
                DemoReply.ok(buildJsonArray {
                    add(buildJsonObject {
                        put("build_uuid", LIVE_BUILD)
                        put("status", "running")
                        put("created_on", "2026-11-11T10:00:00Z")
                    })
                })
            }
        }
        fixture.build(repository = BuildsRepository(api))

        val active = fixture.gateway.activeBuilds(TEST_ACCOUNT)

        assertTrue(active is ApiResult.Success)
        assertEquals(listOf(LIVE_BUILD), (active as ApiResult.Success).value.map { it.buildUuid })
    }
}
