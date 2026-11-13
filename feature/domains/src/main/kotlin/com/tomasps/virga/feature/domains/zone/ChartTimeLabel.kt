package com.tomasps.virga.feature.domains.zone

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AXIS_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())

/**
 * Turns an API timestamp such as `2026-11-10T17:21:03.863541Z` into a short label for the chart axis, in the phone's
 * time zone. A value that does not parse is returned unchanged rather than dropped.
 */
internal fun formatChartTimeLabel(raw: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { AXIS_FORMAT.withZone(zone).format(Instant.parse(raw)) }.getOrDefault(raw)
