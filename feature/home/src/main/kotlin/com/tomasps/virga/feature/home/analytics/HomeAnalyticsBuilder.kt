package com.tomasps.virga.feature.home.analytics

import com.tomasps.virga.core.common.percentChange

/** Result of one analytics source for the current refresh. [Skipped] means the node was not queried at all. */
sealed interface SourceOutcome<out T> {
    data class Loaded<T>(val value: T) : SourceOutcome<T>
    data class Failed(val issue: AnalyticsIssue) : SourceOutcome<Nothing>
    data object Skipped : SourceOutcome<Nothing>
}

private const val PERCENT = 100.0

/**
 * Assembles the Home tiles from the two sources. Pure, so the previous-period, hit-rate and missing-data rules are
 * covered by unit tests. A tile whose source is skipped or failed has a null current value, never zero.
 */
internal fun buildHomeAnalytics(
    hasComparison: Boolean,
    workers: SourceOutcome<WorkersAnalytics>,
    zones: SourceOutcome<ZonesAnalytics>,
): HomeAnalytics {
    val w = (workers as? SourceOutcome.Loaded)?.value
    val z = (zones as? SourceOutcome.Loaded)?.value
    val workersPartial = w?.totalsTruncated == true
    val zonesPartial = z?.partial == true
    // A partial source covers a different set of data now than before, so its delta would compare unlike totals.
    val prevWorkers: (Long?) -> Long? = { if (hasComparison && !workersPartial) it else null }
    val prevZones: (Long?) -> Long? = { if (hasComparison && !zonesPartial) it else null }
    return HomeAnalytics(
        hasComparison = hasComparison,
        workersIssue = (workers as? SourceOutcome.Failed)?.issue ?: AnalyticsIssue.Partial.takeIf { workersPartial },
        zonesIssue = (zones as? SourceOutcome.Failed)?.issue ?: AnalyticsIssue.Partial.takeIf { zonesPartial },
        requests = Metric(
            current = z?.currentRequests?.toDouble(),
            previous = z?.previousRequests?.let(prevZones)?.toDouble(),
            series = z?.hourly?.map { it.requests.toDouble() }.orEmpty(),
        ),
        cacheHitRate = Metric(
            current = z?.let { ratioPercent(it.currentCached, it.currentRequests) },
            previous = z?.let { zs ->
                zs.previousRequests?.let { ratioPercent(zs.previousCached ?: 0L, it) }
            }?.takeIf { hasComparison && !zonesPartial },
            series = z?.hourly.orEmpty().mapNotNull { row -> ratioPercent(row.cached, row.requests) },
        ),
        invocations = Metric(
            current = w?.currentRequests?.toDouble(),
            previous = w?.previousRequests?.let(prevWorkers)?.toDouble(),
            series = w?.hourly?.map { it.requests.toDouble() }.orEmpty(),
        ),
        errors = Metric(
            current = w?.currentErrors?.toDouble(),
            previous = w?.previousErrors?.let(prevWorkers)?.toDouble(),
            series = w?.hourly?.map { it.errors.toDouble() }.orEmpty(),
        ),
        cpuP50Ms = Metric(
            current = w?.currentCpu?.p50Ms,
            previous = if (hasComparison) w?.previousCpu?.p50Ms else null,
            series = emptyList(),
        ),
        cpuP99Ms = Metric(
            current = w?.currentCpu?.p99Ms,
            previous = if (hasComparison) w?.previousCpu?.p99Ms else null,
            series = emptyList(),
        ),
        zonesQueried = z?.zonesQueried ?: 0,
        zonesTotal = z?.zonesTotal ?: 0,
        requestsByWorker = w?.byScript.orEmpty(),
        workerCountsApproximate = w?.topTruncated == true,
        requestsByZone = z?.requestsByZone.orEmpty(),
    )
}

/** Cache hit rate as a percentage. With no requests there is no rate, so the result is null, not 0%. */
internal fun ratioPercent(part: Long, whole: Long): Double? =
    if (whole <= 0L) null else part.toDouble() / whole.toDouble() * PERCENT

/** Relative change for count and duration tiles. Null when either side is missing or the previous value is zero. */
internal fun Metric.relativeChange(): Double? {
    val now = current ?: return null
    val before = previous ?: return null
    return percentChange(before, now)
}

/** Point change for the hit-rate tile, in percentage points. */
internal fun Metric.pointChange(): Double? {
    val now = current ?: return null
    val before = previous ?: return null
    return now - before
}
