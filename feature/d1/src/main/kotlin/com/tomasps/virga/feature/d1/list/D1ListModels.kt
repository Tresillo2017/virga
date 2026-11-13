package com.tomasps.virga.feature.d1.list

import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.components.loadMoreStateOf
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.data.D1Database

/** Databases loaded so far. [isLoadingMore] and [moreFailed] drive the footer; [loadMore] is derived from them. */
data class DatabaseListContent(
    val items: List<D1Database> = emptyList(),
    val nextPage: Int? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val moreFailed: Boolean = false,
) {
    val loadMore: LoadMoreState
        get() = loadMoreStateOf(hasMore = hasMore, isLoading = isLoadingMore, hasFailed = moreFailed)
}

/** Create dialog state. Jurisdiction and location are null when the user keeps the default. */
data class CreateDatabaseState(
    val name: String = "",
    val jurisdiction: String? = null,
    val locationHint: String? = null,
    val nameError: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: ErrorUi? = null,
)

data class D1ListUiState(
    val databases: UiState<DatabaseListContent> = UiState.Loading,
    val create: CreateDatabaseState? = null,
    val deleteTarget: D1Database? = null,
    val isDeleting: Boolean = false,
    val isRefreshing: Boolean = false,
    val actionError: ErrorUi? = null,
    val recentlyDeleted: List<RecentlyDeletedDatabase> = emptyList(),
    val restoringUuid: String? = null,
)

/** Jurisdictions the API accepts at creation (reference enum). Empty means the default (no restriction). */
val D1_JURISDICTIONS: List<String> = listOf("eu", "fedramp", "us")

/** Location hints the API accepts for the primary database instance. */
val D1_LOCATION_HINTS: List<String> = listOf("wnam", "enam", "weur", "eeur", "apac", "oc")

/** Readable names for the API's jurisdiction values. Users see these, never the wire value. */
val D1_JURISDICTION_LABEL_RES: Map<String, Int> = mapOf(
    "eu" to R.string.d1_jurisdiction_eu,
    "fedramp" to R.string.d1_jurisdiction_fedramp,
    "us" to R.string.d1_jurisdiction_us,
)

/** Readable names for the API's location hints, by region. */
val D1_LOCATION_LABEL_RES: Map<String, Int> = mapOf(
    "wnam" to R.string.d1_location_wnam,
    "enam" to R.string.d1_location_enam,
    "weur" to R.string.d1_location_weur,
    "eeur" to R.string.d1_location_eeur,
    "apac" to R.string.d1_location_apac,
    "oc" to R.string.d1_location_oc,
)
