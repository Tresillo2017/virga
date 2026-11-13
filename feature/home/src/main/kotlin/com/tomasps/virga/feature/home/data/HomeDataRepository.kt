package com.tomasps.virga.feature.home.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.home.analytics.AnalyticsWindow
import com.tomasps.virga.feature.home.analytics.WorkersAnalytics
import com.tomasps.virga.feature.home.analytics.ZonesAnalytics
import com.tomasps.virga.feature.home.search.SearchItem
import com.tomasps.virga.feature.home.search.SearchSource

/** Everything the Home screen reads from the API. ViewModels depend on this, so tests can fake it. */
interface HomeDataRepository {
    /** A lightweight list for search and shortcuts. Successful results are cached per account for this session. */
    suspend fun list(accountId: String, source: SearchSource, forceRefresh: Boolean = false): ApiResult<List<SearchItem>>

    suspend fun workerAnalytics(accountId: String, window: AnalyticsWindow): ApiResult<WorkersAnalytics>

    /** [zoneIds] is every zone the account has. The repository reads at most [MAX_ZONES_ON_HOME] of them. */
    suspend fun zoneAnalytics(accountId: String, zoneIds: List<String>, window: AnalyticsWindow): ApiResult<ZonesAnalytics>
}
