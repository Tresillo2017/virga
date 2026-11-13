package com.tomasps.virga.feature.home.analytics

import com.tomasps.virga.core.ui.components.TimeRange
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Two equal periods must fit in the adaptive datasets' 31-day retention, so the longest comparable range is 7 days. */
internal const val COMPARISON_MAX_SPAN_HOURS = 14L * 24L

/**
 * The query window for one analytics refresh. [currentStart] to [end] is the selected range. [previousStart] to
 * [currentStart] is the equal period before it, or null when the range is too long to compare (30 days).
 */
data class AnalyticsWindow(
    val previousStart: Instant?,
    val currentStart: Instant,
    val end: Instant,
) {
    val hasComparison: Boolean get() = previousStart != null
}

/** Builds the window for [range] ending at [now]. Pure, so the boundaries are testable. */
fun analyticsWindow(range: TimeRange, now: Instant): AnalyticsWindow {
    val span = Duration.ofHours(range.hours.toLong())
    val currentStart = now.minus(span)
    val canCompare = range.hours.toLong() * 2 <= COMPARISON_MAX_SPAN_HOURS
    return AnalyticsWindow(
        previousStart = if (canCompare) now.minus(span.multipliedBy(2)) else null,
        currentStart = currentStart,
        end = now,
    )
}

/** Start of the hour containing [instant]. Series are bucketed by hour, whatever granularity the API returns. */
fun hourBucket(instant: Instant): Instant = instant.truncatedTo(ChronoUnit.HOURS)
