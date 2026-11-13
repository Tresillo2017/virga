package com.tomasps.virga.feature.domains.zones

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.feature.domains.R

/**
 * Words for a zone or registrar status. Known values get their translated label; an unknown value is shown as a
 * readable phrase (its raw id, with dashes and underscores turned into spaces) rather than as the API id.
 */
@Composable
fun zoneStatusLabel(status: String?): String = when (status?.lowercase()) {
    ZoneStatus.ACTIVE -> stringResource(R.string.domains_status_active)
    ZoneStatus.PENDING -> stringResource(R.string.domains_status_pending)
    ZoneStatus.INITIALIZING -> stringResource(R.string.domains_status_initializing)
    ZoneStatus.MOVED -> stringResource(R.string.domains_status_moved)
    null, "" -> stringResource(R.string.domains_status_unknown)
    else -> humanizeId(status)
}

/** Dot colour for a zone status: green when live, amber while it is setting up or moved, grey otherwise. */
fun zoneStatusTone(status: String?): StatusTone = when (status?.lowercase()) {
    ZoneStatus.ACTIVE -> StatusTone.Success
    ZoneStatus.PENDING, ZoneStatus.INITIALIZING -> StatusTone.Pending
    ZoneStatus.MOVED -> StatusTone.Warning
    else -> StatusTone.Neutral
}

/** A readable phrase for an API identifier such as `pending-disabled`: "Pending disabled". */
fun humanizeId(raw: String): String = raw
    .replace('_', ' ')
    .replace('-', ' ')
    .trim()
    .lowercase()
    .replaceFirstChar { it.uppercaseChar() }
