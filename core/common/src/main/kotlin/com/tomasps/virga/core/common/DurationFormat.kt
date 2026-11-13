package com.tomasps.virga.core.common

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val MILLIS_PER_MINUTE = SECONDS_PER_MINUTE * MILLIS_PER_SECOND
private const val MILLIS_PER_HOUR = MINUTES_PER_HOUR * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = HOURS_PER_DAY * MILLIS_PER_HOUR

/**
 * Compact duration: "850 ms", "4.2 s", "1m 05s", "2h 03m", "1d 4h".
 */
fun formatDuration(millis: Long): String {
    require(millis >= 0) { "Duration must not be negative: $millis" }
    return when {
        millis < MILLIS_PER_SECOND -> "$millis ms"
        millis < MILLIS_PER_MINUTE -> trimFixed(millis / 1000.0, 1) + " s"
        millis < MILLIS_PER_HOUR -> {
            val minutes = millis / MILLIS_PER_MINUTE
            val seconds = (millis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND
            "${minutes}m ${seconds.toString().padStart(2, '0')}s"
        }
        millis < MILLIS_PER_DAY -> {
            val hours = millis / MILLIS_PER_HOUR
            val minutes = (millis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
            "${hours}h ${minutes.toString().padStart(2, '0')}m"
        }
        else -> {
            val days = millis / MILLIS_PER_DAY
            val hours = (millis % MILLIS_PER_DAY) / MILLIS_PER_HOUR
            "${days}d ${hours}h"
        }
    }
}
