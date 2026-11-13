package com.tomasps.virga.feature.home.home

import androidx.compose.runtime.Immutable
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.home.analytics.HomeAnalytics
import com.tomasps.virga.feature.home.search.SearchItem
import com.tomasps.virga.feature.home.search.SearchSource
import com.tomasps.virga.feature.home.search.SourceState

/** The search box and what it matched. [sources] holds every list, loaded or not. */
@Immutable
data class HomeSearchState(
    val query: String = "",
    val sources: Map<SearchSource, SourceState> = SearchSource.entries.associateWith { SourceState.NotLoaded },
    val results: List<SearchItem> = emptyList(),
)

/** The Watch steps for a build when notifications need permission. State lives here so it survives recreation. */
@Immutable
sealed interface WatchPrompt {
    val build: BuildSummary

    /** Explains why notifications are wanted, before the system permission prompt. */
    data class Rationale(override val build: BuildSummary) : WatchPrompt

    /** The system permission prompt is due to be shown. The screen launches it once per prompt. */
    data class AwaitingPermission(override val build: BuildSummary) : WatchPrompt
}

/**
 * Everything the Home screen shows. [analytics] is null when no account is selected. [accountName] is null when no
 * account is selected, and blank when the account has no name. The Workers and domains cards read the loaded lists in
 * [search] and the counts in [analytics].
 */
@Immutable
data class HomeUiState(
    val accountId: String? = null,
    val accountName: String? = null,
    val accounts: List<CloudflareAccount> = emptyList(),
    val isDemo: Boolean = false,
    val range: TimeRange = TimeRange.TWENTY_FOUR_HOURS,
    val isRefreshing: Boolean = false,
    val analytics: UiState<HomeAnalytics>? = null,
    val building: UiState<List<BuildSummary>>? = null,
    val watchedBuildIds: Set<String> = emptySet(),
    val search: HomeSearchState = HomeSearchState(),
    val watchPrompt: WatchPrompt? = null,
)
