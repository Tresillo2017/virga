package com.tomasps.virga.feature.home.search

import androidx.compose.runtime.Immutable

/** Why a search source has no items. A missing permission is named so the user can grant it. */
sealed interface SourceUnavailable {
    data class MissingPermission(val permission: String) : SourceUnavailable
    data object Failed : SourceUnavailable
}

/** One source's list as Home holds it. Only [Loaded] contributes search hits. */
@Immutable
sealed interface SourceState {
    data object NotLoaded : SourceState
    data object Loading : SourceState
    data class Loaded(val items: List<SearchItem>) : SourceState
    data class Unavailable(val reason: SourceUnavailable) : SourceState
}

/** Maps a failed list call to what the search section can say. Only a 403 names a permission. */
fun sourceUnavailable(source: SearchSource, error: com.tomasps.virga.core.common.result.ApiError): SourceUnavailable = when (error) {
    is com.tomasps.virga.core.common.result.ApiError.Forbidden,
    is com.tomasps.virga.core.common.result.ApiError.Unauthorized,
    -> SourceUnavailable.MissingPermission(source.permission)
    else -> SourceUnavailable.Failed
}
