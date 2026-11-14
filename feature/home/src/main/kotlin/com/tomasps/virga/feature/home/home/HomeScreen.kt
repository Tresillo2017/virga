package com.tomasps.virga.feature.home.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SearchField
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.feature.home.R
import com.tomasps.virga.feature.home.search.SearchItem
import com.tomasps.virga.feature.home.search.SearchSource
import com.tomasps.virga.feature.home.search.SourceState
import com.tomasps.virga.feature.home.search.SourceUnavailable
import com.tomasps.virga.feature.home.search.destination
import org.koin.compose.viewmodel.koinViewModel

internal const val DASHBOARD_URL = "https://dash.cloudflare.com/"

/** Test tag on Home's scrolling list, so UI tests can scroll to rows that sit below the fold. */
internal const val HOME_LIST_TAG = "home-list"

private val HOME_GUTTER = 16.dp
private val ACCOUNT_LABEL_MAX_WIDTH = 220.dp
private val SUBTITLE_ICON_SIZE = 18.dp
private val LIST_SPACING = 8.dp

/** Callbacks from the Home screen. Each one forwards to the ViewModel or the navigator. */
internal data class HomeActions(
    val onAccountSelected: (String) -> Unit,
    val onRangeSelected: (TimeRange) -> Unit,
    val onQueryChanged: (String) -> Unit,
    val onSearchFocused: () -> Unit,
    val onOpen: (NavKey) -> Unit,
    val onOpenDashboard: () -> Unit,
    val onWatchRequested: (BuildSummary, Boolean) -> Unit,
    val onWatchRationaleAnswered: (Boolean) -> Unit,
    val onWatchRationaleDismissed: () -> Unit,
    val onWatchPermissionAnswered: () -> Unit,
    val onStopWatching: (String) -> Unit,
    val onRetryBuilds: () -> Unit,
    val onRefresh: () -> Unit,
)

@Composable
fun HomeRoute(viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    val actions = HomeActions(
        onAccountSelected = viewModel::onAccountSelected,
        onRangeSelected = viewModel::onRangeSelected,
        onQueryChanged = viewModel::onQueryChanged,
        onSearchFocused = viewModel::onSearchFocused,
        onOpen = { key -> navigator.navigate(key) },
        onOpenDashboard = { navigator.openInBrowser(DASHBOARD_URL) },
        onWatchRequested = viewModel::onWatchRequested,
        onWatchRationaleAnswered = viewModel::onWatchRationaleAnswered,
        onWatchRationaleDismissed = viewModel::onWatchRationaleDismissed,
        onWatchPermissionAnswered = viewModel::onWatchPermissionAnswered,
        onStopWatching = viewModel::onStopWatching,
        onRetryBuilds = viewModel::onRetryBuilds,
        onRefresh = viewModel::refresh,
    )
    HomeScaffold(state = state, actions = actions) { padding ->
        HomeBody(state = state, padding = padding, actions = actions)
    }
}

/**
 * Home's frame: a top bar with the title and the account as a tappable subtitle (its menu offers the other accounts),
 * the time range in the actions, and pull to refresh around the body. Home builds its own frame, not ScreenScaffold,
 * because ScreenScaffold's subtitle is plain text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffold(state: HomeUiState, actions: HomeActions, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { HomeTitle(state = state, onAccountSelected = actions.onAccountSelected) },
                actions = { RangeMenu(selected = state.range, onSelected = actions.onRangeSelected) },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            content(padding)
        }
    }
}

@Composable
private fun HomeTitle(state: HomeUiState, onAccountSelected: (String) -> Unit) {
    val unnamed = stringResource(R.string.home_account_unnamed)
    val name = when {
        state.accountName == null -> stringResource(R.string.home_no_account)
        state.accountName.isBlank() -> unnamed
        else -> state.accountName
    }
    Column {
        Text(
            text = stringResource(R.string.home_title_homeroot),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AccountSubtitle(name = name, accounts = state.accounts, currentId = state.accountId, unnamedLabel = unnamed, onSelected = onAccountSelected)
    }
}

/** The account name under the title. With two or more accounts, tapping it opens the switcher; with one it is plain text. */
@Composable
private fun AccountSubtitle(
    name: String,
    accounts: List<CloudflareAccount>,
    currentId: String?,
    unnamedLabel: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (accounts.size < 2) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Box {
        Row(
            modifier = Modifier.clickable(role = Role.Button) { expanded = true }.widthIn(max = ACCOUNT_LABEL_MAX_WIDTH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(SUBTITLE_ICON_SIZE),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name.ifBlank { unnamedLabel }) },
                    onClick = {
                        expanded = false
                        if (account.id != currentId) onSelected(account.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeBody(state: HomeUiState, padding: PaddingValues, actions: HomeActions) {
    val requestWatch = rememberWatchRequest(onRequested = actions.onWatchRequested)
    WatchPromptHost(
        prompt = state.watchPrompt,
        onRationaleAnswered = actions.onWatchRationaleAnswered,
        onRationaleDismissed = actions.onWatchRationaleDismissed,
        onPermissionAnswered = actions.onWatchPermissionAnswered,
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag(HOME_LIST_TAG),
        contentPadding = PaddingValues(
            start = HOME_GUTTER,
            end = HOME_GUTTER,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(LIST_SPACING),
    ) {
        item { HomeSearch(search = state.search, actions = actions) }
        item { SectionHeader(title = stringResource(R.string.home_section_analytics), alignToCardEdge = true) }
        item {
            HomeAnalyticsSection(
                analytics = state.analytics,
                range = state.range,
                workersKnownEmpty = workersEmpty(state),
                zonesKnownEmpty = zonesEmpty(state),
                onOpenDashboard = actions.onOpenDashboard,
            )
        }
        item { SectionHeader(title = stringResource(R.string.home_section_building)) }
        item {
            HomeBuildsSection(
                building = state.building,
                watchedBuildIds = state.watchedBuildIds,
                onWatchRequested = requestWatch,
                onStopWatching = { build -> actions.onStopWatching(build.buildUuid) },
                onOpenBuild = { build ->
                    state.accountId?.let { accountId ->
                        actions.onOpen(BuildDetail(accountId, build.buildUuid, build.scriptName))
                    }
                },
                onRetry = actions.onRetryBuilds,
            )
        }
        item { HomeResourceLists(state = state, actions = actions) }
    }
}

internal fun workersEmpty(state: HomeUiState): Boolean =
    (state.search.sources[SearchSource.WORKERS] as? SourceState.Loaded)?.items?.isEmpty() == true

internal fun zonesEmpty(state: HomeUiState): Boolean =
    (state.search.sources[SearchSource.ZONES] as? SourceState.Loaded)?.items?.isEmpty() == true

/** The compact time range: the selected window, with the other windows in a menu. */
@Composable
private fun RangeMenu(selected: TimeRange, onSelected: (TimeRange) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(selected.labelResId)
    val description = stringResource(R.string.home_range_description, label)
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = description }) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeRange.entries.forEach { range ->
                DropdownMenuItem(
                    text = { Text(stringResource(range.labelResId)) },
                    onClick = {
                        expanded = false
                        if (range != selected) onSelected(range)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeSearch(search: HomeSearchState, actions: HomeActions) {
    val focus = LocalFocusManager.current
    Column {
        SearchField(
            query = search.query,
            onQueryChange = actions.onQueryChanged,
            placeholder = stringResource(R.string.home_search_placeholder),
            modifier = Modifier.onFocusChanged { if (it.isFocused) actions.onSearchFocused() },
        )
        if (search.query.isNotBlank()) {
            SearchResults(search = search, onOpen = { item ->
                focus.clearFocus()
                actions.onOpen(item.destination())
            })
        }
    }
}

@Composable
private fun SearchResults(search: HomeSearchState, onOpen: (SearchItem) -> Unit) {
    Column {
        if (search.results.isEmpty()) {
            Text(
                text = stringResource(R.string.home_search_no_results, search.query),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        search.results.forEach { item ->
            ListRow(
                headline = item.title,
                supporting = stringResource(sourceLabel(item.source)),
                onClick = { onOpen(item) },
            )
        }
        search.sources.forEach { (source, state) ->
            (state as? SourceState.Unavailable)?.let { unavailable ->
                Text(
                    text = sourceIssueText(source, unavailable.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun sourceIssueText(source: SearchSource, reason: SourceUnavailable): String {
    val label = stringResource(sourceLabel(source))
    return when (reason) {
        is SourceUnavailable.MissingPermission ->
            stringResource(R.string.home_search_source_permission, label, reason.permission)
        SourceUnavailable.Failed -> stringResource(R.string.home_search_source_failed, label)
    }
}

internal fun sourceLabel(source: SearchSource): Int = source.labelRes
