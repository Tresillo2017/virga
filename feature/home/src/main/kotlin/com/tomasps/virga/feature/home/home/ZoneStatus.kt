package com.tomasps.virga.feature.home.home

import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.feature.home.R

private const val STATUS_ACTIVE = "active"
private const val STATUS_PENDING = "pending"
private const val STATUS_INITIALIZING = "initializing"
private const val STATUS_MOVED = "moved"
private const val STATUS_DEACTIVATED = "deactivated"

/**
 * The dot for a zone's status, or null when the status is missing or not one Home knows. A null gets no dot, so a
 * status is never guessed.
 */
internal fun zoneStatusTone(status: String?): StatusTone? = when (status) {
    STATUS_ACTIVE -> StatusTone.Success
    STATUS_PENDING, STATUS_INITIALIZING -> StatusTone.Pending
    STATUS_MOVED, STATUS_DEACTIVATED -> StatusTone.Warning
    else -> null
}

/** The friendly label for a zone's status, or null when there is none to show. The raw API word is never shown. */
internal fun zoneStatusLabelRes(status: String?): Int? = when (status) {
    STATUS_ACTIVE -> R.string.home_zone_status_active
    STATUS_PENDING -> R.string.home_zone_status_pending
    STATUS_INITIALIZING -> R.string.home_zone_status_initializing
    STATUS_MOVED -> R.string.home_zone_status_moved
    STATUS_DEACTIVATED -> R.string.home_zone_status_deactivated
    else -> null
}
