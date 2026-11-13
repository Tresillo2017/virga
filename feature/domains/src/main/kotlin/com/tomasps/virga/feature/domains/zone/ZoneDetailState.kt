package com.tomasps.virga.feature.domains.zone

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.analytics.ZoneTraffic
import com.tomasps.virga.feature.domains.zones.PurgeProblem
import com.tomasps.virga.feature.domains.zones.PurgeRequest
import com.tomasps.virga.feature.domains.zones.RegistrarRegistration
import com.tomasps.virga.feature.domains.zones.WorkerRoute
import com.tomasps.virga.feature.domains.zones.Zone

/** A change to the zone that needs the owner to confirm first. The screen picks plain or typed-name confirmation from it. */
sealed interface ZoneAction {
    data class SetPaused(val paused: Boolean) : ZoneAction
    data class Purge(val request: PurgeRequest) : ZoneAction
    data object Delete : ZoneAction
}

/** Development mode as shown on the overview. [remainingSeconds] is null when the mode is not running. */
data class DevModeStatus(
    val on: Boolean,
    val remainingSeconds: Long?,
    val editable: Boolean,
)

/**
 * Overview state for one zone. [registrar] content is null when the domain is not registered with Cloudflare Registrar,
 * which is an ordinary answer, not an error.
 */
data class ZoneDetailUiState(
    val zone: UiState<Zone> = UiState.Loading,
    val traffic: UiState<ZoneTraffic> = UiState.Loading,
    val timeRange: TimeRange = TimeRange.TWENTY_FOUR_HOURS,
    val devMode: UiState<DevModeStatus> = UiState.Loading,
    val routes: UiState<List<WorkerRoute>> = UiState.Loading,
    val registrar: UiState<RegistrarRegistration?> = UiState.Loading,
    val pendingAction: ZoneAction? = null,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val message: UiText? = null,
    val deleted: Boolean = false,
)

/** Maps a purge selection problem to the text the purge dialog shows. */
fun PurgeProblem.toText(): UiText = when (this) {
    PurgeProblem.EMPTY -> UiText.Resource(R.string.domains_purge_error_empty)
    PurgeProblem.TOO_MANY -> UiText.Resource(R.string.domains_purge_error_too_many)
    PurgeProblem.NEEDS_SCHEME -> UiText.Resource(R.string.domains_purge_error_needs_scheme)
    PurgeProblem.MUST_NOT_HAVE_SCHEME -> UiText.Resource(R.string.domains_purge_error_no_scheme)
}

/** Registrar lookups that mean "not registered here": a missing registration, or an account without access to it. */
internal fun ApiError.meansNotRegisteredHere(): Boolean = this is ApiError.NotFound || this is ApiError.Forbidden
