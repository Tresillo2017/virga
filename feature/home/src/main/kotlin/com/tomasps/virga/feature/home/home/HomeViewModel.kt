package com.tomasps.virga.feature.home.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.components.DefaultTimeRangeSource
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.home.analytics.AnalyticsIssueClassifier
import com.tomasps.virga.feature.home.analytics.AnalyticsScope
import com.tomasps.virga.feature.home.analytics.AnalyticsWindow
import com.tomasps.virga.feature.home.analytics.HomeAnalytics
import com.tomasps.virga.feature.home.analytics.SourceOutcome
import com.tomasps.virga.feature.home.analytics.analyticsWindow
import com.tomasps.virga.feature.home.analytics.buildHomeAnalytics
import com.tomasps.virga.feature.home.data.HomeDataRepository
import com.tomasps.virga.feature.home.search.SearchItem
import com.tomasps.virga.feature.home.search.SearchSource
import com.tomasps.virga.feature.home.search.SourceState
import com.tomasps.virga.feature.home.search.matchSearchItems
import com.tomasps.virga.feature.home.search.sourceUnavailable
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Account home. Loads the Workers and zone lists (needed for shortcuts and search), the analytics for the selected
 * range, and the active builds. Refreshes only on account change, range change, or pull-to-refresh.
 */
class HomeViewModel(
    private val session: SessionRepository,
    private val data: HomeDataRepository,
    private val builds: BuildsGateway,
    private val now: () -> Instant = Instant::now,
    private val defaultRange: DefaultTimeRangeSource? = null,
) : ViewModel() {

    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var analyticsJob: Job? = null
    private val listJobs = mutableListOf<Job>()
    private var zoneIds: List<String> = emptyList()
    private var workersKnownEmpty = false

    /** The account whose Workers and zone lists are loaded. Analytics wait for these, because they need the zone ids. */
    private var listsReadyFor: String? = null
    private val analyticsByAccountAndRange = mutableMapOf<AnalyticsKey, HomeAnalytics>()

    private data class AnalyticsKey(val accountId: String, val range: TimeRange)

    init {
        viewModelScope.launch {
            session.state.collect { sessionState -> applyHeader(sessionState) }
        }
        viewModelScope.launch {
            // The saved default is read before the first load, so the first analytics are for the range Home opens on.
            val startRange = initialRange()
            mutableState.update { it.copy(range = startRange) }
            session.state.map { (it as? SessionState.SignedIn)?.selectedAccountId }
                .distinctUntilChanged()
                .collectLatest { accountId -> if (accountId != null) loadAll(accountId, force = false) else clearAccount() }
        }
        viewModelScope.launch {
            builds.watchedBuildIds().collect { ids -> mutableState.update { it.copy(watchedBuildIds = ids) } }
        }
    }

    /** The first value of the saved default range when one is provided, otherwise the range the state starts with. */
    private suspend fun initialRange(): TimeRange =
        defaultRange?.defaultRange?.first() ?: mutableState.value.range

    fun onRangeSelected(range: TimeRange) {
        if (mutableState.value.range == range) return
        mutableState.update { it.copy(range = range) }
        val accountId = mutableState.value.accountId ?: return
        // While the lists load, loadAll computes analytics for whichever range is selected by then.
        if (listsReadyFor != accountId) return
        analyticsByAccountAndRange[AnalyticsKey(accountId, range)]?.let { cached ->
            mutableState.update { it.copy(analytics = UiState.Content(cached)) }
            return
        }
        loadAnalytics(accountId, range)
    }

    fun refresh() {
        val accountId = mutableState.value.accountId ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadAll(accountId, force = true) }
    }

    fun onQueryChanged(text: String) {
        mutableState.update { it.copy(search = it.search.copy(query = text, results = matchSearchItems(loadedItems(it), text))) }
    }

    /** Loads the lists that search needs on first focus. Workers and zones are already loaded with the screen. */
    fun onSearchFocused() {
        val accountId = mutableState.value.accountId ?: return
        val missing = mutableState.value.search.sources.filterValues { it == SourceState.NotLoaded }.keys
        if (missing.isEmpty()) return
        val job = viewModelScope.launch {
            coroutineScope {
                missing.forEach { source -> launch { loadList(accountId, source, force = false) } }
            }
        }
        listJobs += job
    }

    fun onAccountSelected(accountId: String) {
        viewModelScope.launch { session.selectAccount(accountId) }
    }

    /** Watch was tapped. [permissionNeeded] is true on Android 13+ when notifications are not yet allowed. */
    fun onWatchRequested(build: BuildSummary, permissionNeeded: Boolean) {
        if (!permissionNeeded) return watchNow(build)
        mutableState.update { it.copy(watchPrompt = WatchPrompt.Rationale(build)) }
    }

    /** The user answered the rationale. "Continue" moves to the system prompt; anything else watches without it. */
    fun onWatchRationaleAnswered(continueToPermission: Boolean) {
        val prompt = mutableState.value.watchPrompt as? WatchPrompt.Rationale ?: return
        if (continueToPermission) {
            mutableState.update { it.copy(watchPrompt = WatchPrompt.AwaitingPermission(prompt.build)) }
        } else {
            mutableState.update { it.copy(watchPrompt = null) }
            watchNow(prompt.build)
        }
    }

    /** The rationale was dismissed without a choice (back or a tap outside). That cancels the Watch, so nothing is watched. */
    fun onWatchRationaleDismissed() {
        if (mutableState.value.watchPrompt !is WatchPrompt.Rationale) return
        mutableState.update { it.copy(watchPrompt = null) }
    }

    /** Stop watching was tapped on a build. The build stays in the list; only the pinned watch goes. */
    fun onStopWatching(buildUuid: String) {
        builds.stopWatching(buildUuid)
    }

    /** The system permission answer arrived, granted or not. The build is watched either way. */
    fun onWatchPermissionAnswered() {
        val prompt = mutableState.value.watchPrompt as? WatchPrompt.AwaitingPermission ?: return
        mutableState.update { it.copy(watchPrompt = null) }
        watchNow(prompt.build)
    }

    fun onRetryBuilds() {
        val accountId = mutableState.value.accountId ?: return
        viewModelScope.launch { loadBuilds(accountId) }
    }

    private fun watchNow(build: BuildSummary) {
        val accountId = mutableState.value.accountId ?: return
        builds.startWatching(accountId, build)
    }

    private fun applyHeader(sessionState: SessionState) {
        val signedIn = sessionState as? SessionState.SignedIn
        mutableState.update { current ->
            current.copy(
                accountName = signedIn?.selectedAccount?.name,
                accounts = signedIn?.accounts.orEmpty(),
                isDemo = signedIn?.isDemo == true,
            )
        }
    }

    /** Stops analytics and list work for the previous account. Does not touch [loadJob], which may be the caller. */
    private fun cancelAccountWork() {
        analyticsJob?.cancel()
        listJobs.forEach { it.cancel() }
        listJobs.clear()
        listsReadyFor = null
    }

    private fun clearAccount() {
        loadJob?.cancel()
        cancelAccountWork()
        analyticsByAccountAndRange.clear()
        zoneIds = emptyList()
        workersKnownEmpty = false
        mutableState.update { HomeUiState(isDemo = it.isDemo, accounts = it.accounts, accountName = null) }
    }

    private suspend fun loadAll(accountId: String, force: Boolean) {
        cancelAccountWork()
        if (force) analyticsByAccountAndRange.clear()
        mutableState.update {
            it.copy(
                accountId = accountId,
                isRefreshing = force,
                analytics = UiState.Loading,
                building = UiState.Loading,
                search = it.search.copy(sources = HomeSearchState().sources, results = emptyList()),
            )
        }
        val zones = loadList(accountId, SearchSource.ZONES, force)
        val workers = loadList(accountId, SearchSource.WORKERS, force)
        zoneIds = (zones as? SourceState.Loaded)?.items?.map { it.id }.orEmpty()
        workersKnownEmpty = (workers as? SourceState.Loaded)?.items?.isEmpty() == true
        listsReadyFor = accountId
        val range = mutableState.value.range
        val analytics = computeAnalytics(accountId, range)
        storeAnalytics(accountId, range, analytics)
        mutableState.update { it.copy(isRefreshing = false) }
        loadBuilds(accountId)
    }

    private fun loadAnalytics(accountId: String, range: TimeRange) {
        analyticsJob?.cancel()
        mutableState.update { it.copy(analytics = UiState.Loading) }
        analyticsJob = viewModelScope.launch {
            val analytics = computeAnalytics(accountId, range)
            storeAnalytics(accountId, range, analytics)
        }
    }

    /** Caches and shows [analytics] only while [accountId] and [range] are still the selection. */
    private fun storeAnalytics(accountId: String, range: TimeRange, analytics: HomeAnalytics) {
        val current = mutableState.value
        if (current.accountId != accountId) return
        analyticsByAccountAndRange[AnalyticsKey(accountId, range)] = analytics
        if (current.range != range) return
        mutableState.update { it.copy(analytics = UiState.Content(analytics)) }
    }

    private suspend fun computeAnalytics(accountId: String, range: TimeRange): HomeAnalytics {
        val window: AnalyticsWindow = analyticsWindow(range, now())
        val workers = if (workersKnownEmpty) SourceOutcome.Skipped else when (val result = data.workerAnalytics(accountId, window)) {
            is ApiResult.Success -> SourceOutcome.Loaded(result.value)
            is ApiResult.Failure -> SourceOutcome.Failed(AnalyticsIssueClassifier.classify(result.error, AnalyticsScope.ACCOUNT))
        }
        val zones = if (zoneIds.isEmpty()) SourceOutcome.Skipped else when (val result = data.zoneAnalytics(accountId, zoneIds, window)) {
            is ApiResult.Success -> SourceOutcome.Loaded(result.value)
            is ApiResult.Failure -> SourceOutcome.Failed(AnalyticsIssueClassifier.classify(result.error, AnalyticsScope.ZONE))
        }
        return buildHomeAnalytics(window.hasComparison, workers, zones)
    }

    private suspend fun loadList(accountId: String, source: SearchSource, force: Boolean): SourceState {
        setSource(accountId, source, SourceState.Loading)
        val state = when (val result = data.list(accountId, source, forceRefresh = force)) {
            is ApiResult.Success -> SourceState.Loaded(result.value)
            is ApiResult.Failure -> SourceState.Unavailable(sourceUnavailable(source, result.error))
        }
        setSource(accountId, source, state)
        return state
    }

    /** Writes one source's state only while [accountId] is still the selected account, so a late list cannot cross accounts. */
    private fun setSource(accountId: String, source: SearchSource, state: SourceState) {
        mutableState.update { current ->
            if (current.accountId != accountId) return@update current
            val search = current.search.copy(sources = current.search.sources + (source to state))
            current.copy(search = search.copy(results = matchSearchItems(loadedItems(search), search.query)))
        }
    }

    private suspend fun loadBuilds(accountId: String) {
        val outcome = when (val result = builds.activeBuilds(accountId)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { if (it.accountId == accountId) it.copy(building = outcome) else it }
    }
}

/** Every item from the lists that loaded, in source order. Failed or pending sources contribute nothing. */
internal fun loadedItems(state: HomeUiState): List<SearchItem> = loadedItems(state.search)

internal fun loadedItems(search: HomeSearchState): List<SearchItem> = SearchSource.entries.flatMap { source ->
    (search.sources[source] as? SourceState.Loaded)?.items.orEmpty()
}
