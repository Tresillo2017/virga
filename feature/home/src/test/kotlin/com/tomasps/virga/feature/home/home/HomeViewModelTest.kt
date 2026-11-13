package com.tomasps.virga.feature.home.home

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.home.analytics.AnalyticsWindow
import com.tomasps.virga.feature.home.analytics.CpuQuantiles
import com.tomasps.virga.feature.home.analytics.ScriptRequests
import com.tomasps.virga.feature.home.analytics.WorkerHourRow
import com.tomasps.virga.feature.home.analytics.WorkersAnalytics
import com.tomasps.virga.feature.home.analytics.ZoneHourRow
import com.tomasps.virga.feature.home.analytics.ZonesAnalytics
import com.tomasps.virga.feature.home.data.HomeDataRepository
import com.tomasps.virga.feature.home.search.SearchItem
import com.tomasps.virga.feature.home.search.SearchSource
import com.tomasps.virga.feature.home.search.SourceState
import com.tomasps.virga.feature.home.search.SourceUnavailable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

private const val WAIT_MS = 2_000L
private val NOW: Instant = Instant.parse("2026-11-11T12:00:00Z")
private const val ZONE_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a signed-in account loads its lists, analytics and active builds`() = runBlocking {
        // Arrange
        val data = FakeHomeData()
        val builds = FakeBuildsGateway(active = listOf(sampleBuild("orbit-api", "b-1")))
        val session = FakeSession(signedIn("acc-1"))

        // Act
        val viewModel = HomeViewModel(session, data, builds, now = { NOW })

        // Assert
        val state = awaitState(viewModel) { it.analytics is UiState.Content && it.building is UiState.Content }
        assertEquals("acc-1", state.accountId)
        assertEquals("Alpha", state.accountName)
        val analytics = (state.analytics as UiState.Content).data
        assertEquals(200.0, analytics.invocations.current!!, 0.0001)
        assertEquals(listOf("orbit-api"), (state.building as UiState.Content).data.map { it.scriptName })
        assertTrue(state.search.sources[SearchSource.ZONES] is SourceState.Loaded)
        assertEquals(SourceState.NotLoaded, state.search.sources[SearchSource.R2])
    }

    @Test
    fun `typing a query finds loaded Workers and zones by name`() = runBlocking {
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), FakeHomeData(), FakeBuildsGateway(), { NOW })
        awaitState(viewModel) { it.search.sources[SearchSource.WORKERS] is SourceState.Loaded }

        viewModel.onQueryChanged("ORBIT")
        val state = viewModel.state.value

        assertEquals(listOf("orbit-api"), state.search.results.map { it.title })
    }

    @Test
    fun `a missing permission on one list is shown for that list and the rest still load`() = runBlocking {
        val data = FakeHomeData(listFailure = mapOf(SearchSource.ZONES to ApiError.Forbidden()))
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), data, FakeBuildsGateway(), { NOW })

        val state = awaitState(viewModel) { it.search.sources[SearchSource.ZONES] is SourceState.Unavailable }

        assertEquals(
            SourceState.Unavailable(SourceUnavailable.MissingPermission("Zone Read")),
            state.search.sources[SearchSource.ZONES],
        )
        assertTrue(state.search.sources[SearchSource.WORKERS] is SourceState.Loaded)
    }

    @Test
    fun `choosing a range reloads analytics for that range`() = runBlocking {
        val data = FakeHomeData()
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), data, FakeBuildsGateway(), { NOW })
        awaitState(viewModel) { it.analytics is UiState.Content }

        viewModel.onRangeSelected(TimeRange.SEVEN_DAYS)
        val state = awaitState(viewModel) { it.range == TimeRange.SEVEN_DAYS && it.analytics is UiState.Content }

        assertEquals(TimeRange.SEVEN_DAYS, state.range)
        assertEquals(168L, data.lastWindow!!.let { (it.end.epochSecond - it.currentStart.epochSecond) / 3600 })
    }

    @Test
    fun `a 30 day range has no previous period in the analytics`() = runBlocking {
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), FakeHomeData(), FakeBuildsGateway(), { NOW })
        awaitState(viewModel) { it.analytics is UiState.Content }

        viewModel.onRangeSelected(TimeRange.THIRTY_DAYS)
        val state = awaitState(viewModel) { it.range == TimeRange.THIRTY_DAYS && it.analytics is UiState.Content }

        assertEquals(false, (state.analytics as UiState.Content).data.hasComparison)
    }

    @Test
    fun `a failed build list shows an error that can be retried`() = runBlocking {
        val builds = FakeBuildsGateway(activeFailure = ApiError.Server(500))
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), FakeHomeData(), builds, { NOW })

        val state = awaitState(viewModel) { it.building is UiState.Failure }

        assertTrue(state.building is UiState.Failure)
    }

    @Test
    fun `watching a build hands it to the builds gateway`() = runBlocking {
        val builds = FakeBuildsGateway()
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), FakeHomeData(), builds, { NOW })
        val build = sampleBuild("orbit-api", "b-9")

        viewModel.onWatchRequested(build, permissionNeeded = false)

        assertEquals(listOf("acc-1" to "b-9"), builds.watched)
    }

    @Test
    fun `signing out clears the account, analytics and search`() = runBlocking {
        val session = FakeSession(signedIn("acc-1"))
        val viewModel = HomeViewModel(session, FakeHomeData(), FakeBuildsGateway(), { NOW })
        awaitState(viewModel) { it.analytics is UiState.Content }

        session.emit(SessionState.SignedOut)
        val state = awaitState(viewModel) { it.accountId == null }

        assertNull(state.analytics)
        assertNull(state.building)
        assertTrue(state.search.results.isEmpty())
    }

    @Test
    fun `a range cached for one account is not shown for the next account`() = runBlocking {
        val data = FakeHomeData(requestsByAccount = mapOf("acc-1" to 200L, "acc-2" to 700L))
        val session = FakeSession(signedIn("acc-1"))
        val viewModel = HomeViewModel(session, data, FakeBuildsGateway(), { NOW })
        awaitState(viewModel) { it.analytics is UiState.Content }
        viewModel.onRangeSelected(TimeRange.THIRTY_DAYS)
        awaitState(viewModel) { it.range == TimeRange.THIRTY_DAYS && it.analytics is UiState.Content }
        viewModel.onRangeSelected(TimeRange.TWENTY_FOUR_HOURS)
        awaitState(viewModel) { it.range == TimeRange.TWENTY_FOUR_HOURS && it.analytics is UiState.Content }

        session.emit(signedIn("acc-2"))
        awaitState(viewModel) { it.accountId == "acc-2" && it.analytics is UiState.Content }
        viewModel.onRangeSelected(TimeRange.THIRTY_DAYS)
        val state = awaitState(viewModel) { it.range == TimeRange.THIRTY_DAYS && invocationsOf(it) == 700.0 }

        assertEquals("acc-2", state.accountId)
        assertEquals(700.0, invocationsOf(state)!!, 0.0001)
    }

    @Test
    fun `a search list still loading for the previous account never reaches the new one`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val session = FakeSession(signedIn("acc-1"))
        val viewModel = HomeViewModel(session, FakeHomeData(pagesGate = gate), FakeBuildsGateway(), { NOW })
        awaitState(viewModel) { it.search.sources[SearchSource.WORKERS] is SourceState.Loaded }
        viewModel.onSearchFocused()

        session.emit(signedIn("acc-2"))
        awaitState(viewModel) { it.accountId == "acc-2" && it.analytics is UiState.Content }
        gate.complete(Unit)

        assertEquals(SourceState.NotLoaded, viewModel.state.value.search.sources[SearchSource.PAGES])
    }

    @Test
    fun `watch asks for the rationale first and watches even when the user declines`() = runBlocking {
        val builds = FakeBuildsGateway()
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), FakeHomeData(), builds, { NOW })
        awaitState(viewModel) { it.accountId == "acc-1" }
        val build = sampleBuild("orbit-api", "b-7")

        viewModel.onWatchRequested(build, permissionNeeded = true)
        assertEquals(WatchPrompt.Rationale(build), viewModel.state.value.watchPrompt)
        assertTrue(builds.watched.isEmpty())

        viewModel.onWatchRationaleAnswered(continueToPermission = false)
        assertNull(viewModel.state.value.watchPrompt)
        assertEquals(listOf("acc-1" to "b-7"), builds.watched)
    }

    @Test
    fun `continuing from the rationale watches after the permission answer, granted or not`() = runBlocking {
        val builds = FakeBuildsGateway()
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), FakeHomeData(), builds, { NOW })
        awaitState(viewModel) { it.accountId == "acc-1" }
        val build = sampleBuild("orbit-api", "b-8")

        viewModel.onWatchRequested(build, permissionNeeded = true)
        viewModel.onWatchRationaleAnswered(continueToPermission = true)
        assertEquals(WatchPrompt.AwaitingPermission(build), viewModel.state.value.watchPrompt)
        assertTrue(builds.watched.isEmpty())

        viewModel.onWatchPermissionAnswered()
        assertNull(viewModel.state.value.watchPrompt)
        assertEquals(listOf("acc-1" to "b-8"), builds.watched)
    }

    @Test
    fun `watch skips the rationale when notifications are already allowed`() = runBlocking {
        val builds = FakeBuildsGateway()
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), FakeHomeData(), builds, { NOW })
        awaitState(viewModel) { it.accountId == "acc-1" }

        viewModel.onWatchRequested(sampleBuild("orbit-api", "b-9"), permissionNeeded = false)

        assertNull(viewModel.state.value.watchPrompt)
        assertEquals(listOf("acc-1" to "b-9"), builds.watched)
    }

    @Test
    fun `dismissing the rationale without a choice cancels the Watch`() = runBlocking {
        val builds = FakeBuildsGateway()
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), FakeHomeData(), builds, { NOW })
        awaitState(viewModel) { it.accountId == "acc-1" }

        viewModel.onWatchRequested(sampleBuild("orbit-api", "b-10"), permissionNeeded = true)
        viewModel.onWatchRationaleDismissed()

        assertNull(viewModel.state.value.watchPrompt)
        assertTrue("a dismissed rationale watches nothing", builds.watched.isEmpty())
    }

    @Test
    fun `stop watching hands the build to the builds gateway`() = runBlocking {
        val builds = FakeBuildsGateway()
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), FakeHomeData(), builds, { NOW })
        awaitState(viewModel) { it.accountId == "acc-1" }

        viewModel.onStopWatching("b-11")

        assertEquals(listOf("b-11"), builds.stopped)
    }

    @Test
    fun `refresh asks the data again for the selected account`() = runBlocking {
        val data = FakeHomeData()
        val viewModel = HomeViewModel(FakeSession(signedIn("acc-1")), data, FakeBuildsGateway(), { NOW })
        awaitState(viewModel) { it.analytics is UiState.Content }
        val callsBefore = data.workerAnalyticsCalls

        viewModel.refresh()

        assertEquals(callsBefore + 1, data.workerAnalyticsCalls)
    }

    private fun invocationsOf(state: HomeUiState): Double? =
        (state.analytics as? UiState.Content)?.data?.invocations?.current

    private fun signedIn(accountId: String) = SessionState.SignedIn(
        accounts = listOf(CloudflareAccount(accountId, "Alpha"), CloudflareAccount("acc-2", "Beta")),
        selectedAccountId = accountId,
        isDemo = false,
    )

    private fun sampleBuild(script: String, uuid: String) = BuildSummary(
        buildUuid = uuid,
        scriptName = script,
        status = BuildStatus.RUNNING,
        outcome = BuildOutcome.UNKNOWN,
        createdOn = NOW,
    )

    private suspend fun awaitState(viewModel: HomeViewModel, predicate: (HomeUiState) -> Boolean): HomeUiState =
        withTimeout(WAIT_MS) { viewModel.state.first(predicate) }
}

private class FakeSession(initial: SessionState) : SessionRepository {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    fun emit(next: SessionState) {
        mutableState.value = next
    }

    override suspend fun restore() = Unit
    override suspend fun signInWithToken(token: String): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun enterDemo(): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun selectAccount(accountId: String) = Unit
    override suspend fun signOut() {
        mutableState.value = SessionState.SignedOut
    }
}

private class FakeHomeData(
    private val listFailure: Map<SearchSource, ApiError> = emptyMap(),
    private val requestsByAccount: Map<String, Long> = emptyMap(),
    private val pagesGate: CompletableDeferred<Unit>? = null,
) : HomeDataRepository {
    var lastWindow: AnalyticsWindow? = null

    override suspend fun list(accountId: String, source: SearchSource, forceRefresh: Boolean): ApiResult<List<SearchItem>> {
        listFailure[source]?.let { return ApiResult.Failure(it) }
        if (source == SearchSource.PAGES) pagesGate?.await()
        return ApiResult.Success(
            when (source) {
                SearchSource.WORKERS -> listOf(SearchItem(source, "orbit-api", "orbit-api"))
                SearchSource.ZONES -> listOf(SearchItem(source, ZONE_ID, "harbor-lane.example"))
                SearchSource.PAGES -> listOf(SearchItem(source, "field-guide", "field-guide"))
                else -> emptyList()
            },
        )
    }

    var workerAnalyticsCalls = 0

    override suspend fun workerAnalytics(accountId: String, window: AnalyticsWindow): ApiResult<WorkersAnalytics> {
        workerAnalyticsCalls += 1
        lastWindow = window
        val requests = requestsByAccount[accountId] ?: 200L
        return ApiResult.Success(
            WorkersAnalytics(
                currentRequests = requests,
                currentErrors = 2,
                previousRequests = 100,
                previousErrors = 1,
                currentCpu = CpuQuantiles(2.0, 18.0),
                previousCpu = CpuQuantiles(2.0, 18.0),
                hourly = listOf(WorkerHourRow(NOW, 200, 2)),
                byScript = listOf(ScriptRequests("orbit-api", requests)),
            ),
        )
    }

    override suspend fun zoneAnalytics(
        accountId: String,
        zoneIds: List<String>,
        window: AnalyticsWindow,
    ): ApiResult<ZonesAnalytics> = ApiResult.Success(
        ZonesAnalytics(
            currentRequests = 1_000,
            currentCached = 600,
            previousRequests = 800,
            previousCached = 400,
            hourly = listOf(ZoneHourRow(NOW, 1_000, 600)),
            requestsByZone = mapOf(ZONE_ID to 1_000L),
            zonesQueried = zoneIds.size,
            zonesTotal = zoneIds.size,
        ),
    )
}

private class FakeBuildsGateway(
    private val active: List<BuildSummary> = emptyList(),
    private val activeFailure: ApiError? = null,
) : BuildsGateway {
    val watched = mutableListOf<Pair<String, String>>()
    val stopped = mutableListOf<String>()
    private val ids = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>> =
        ApiResult.Success(emptyList())

    override suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>> =
        activeFailure?.let { ApiResult.Failure(it) } ?: ApiResult.Success(active)

    override fun watchedBuildIds(): Flow<Set<String>> = ids

    override fun startWatching(accountId: String, build: BuildSummary) {
        watched += accountId to build.buildUuid
    }

    override fun stopWatching(buildUuid: String) {
        stopped += buildUuid
    }
}
