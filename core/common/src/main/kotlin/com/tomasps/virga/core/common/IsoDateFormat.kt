package com.tomasps.virga.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/**
 * Turns an API ISO 8601 timestamp into a date the user can read, in the device's zone (for example "Nov 10, 2026").
 * A value that does not parse, or a blank one, is returned unchanged so nothing is hidden.
 */
fun formatIsoDate(raw: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (raw.isNullOrBlank()) return raw
    val instant = try {
        Instant.parse(raw)
    } catch (_: DateTimeParseException) {
        return raw
    }
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zone).format(instant)
}
