package com.tomasps.virga.feature.workers.metrics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val LABEL_PATTERN = "MMM d, HH:mm"

/**
 * A readable x-axis label for one metrics bucket, in the device's zone (for example "Nov 11, 09:00"). A value that does
 * not parse is returned unchanged, so a chart label is never blank.
 */
internal fun formatBucketLabel(raw: String, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val instant = try {
        Instant.parse(raw)
    } catch (_: DateTimeParseException) {
        return raw
    }
    return DateTimeFormatter.ofPattern(LABEL_PATTERN, locale).withZone(zone).format(instant)
}
