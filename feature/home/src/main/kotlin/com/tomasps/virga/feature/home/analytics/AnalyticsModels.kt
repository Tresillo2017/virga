package com.tomasps.virga.feature.home.analytics

import androidx.compose.runtime.Immutable
import java.time.Instant

/** One hourly Workers row summed over the account's Workers. */
@Immutable
data class WorkerHourRow(val at: Instant, val requests: Long, val errors: Long)

/** One hourly zone row summed over the zones that were queried. */
@Immutable
data class ZoneHourRow(val at: Instant, val requests: Long, val cached: Long)

data class CpuQuantiles(val p50Ms: Double?, val p99Ms: Double?)

data class ScriptRequests(val scriptName: String, val requests: Long)

/** Worker analytics for one window. [previous] is null when the window has no comparison period. */
data class WorkersAnalytics(
    val currentRequests: Long,
    val currentErrors: Long,
    val previousRequests: Long?,
    val previousErrors: Long?,
    val currentCpu: CpuQuantiles,
    val previousCpu: CpuQuantiles?,
    val hourly: List<WorkerHourRow>,
    val byScript: List<ScriptRequests>,
    /** True when the hourly rows hit [MAX_HOURLY_ROWS], so the totals may be short. */
    val totalsTruncated: Boolean = false,
    /** True when the per-script rows hit [TOP_SCRIPT_LIMIT], so the top list is approximate. */
    val topTruncated: Boolean = false,
)

/** Zone analytics for one window across the zones that were queried. */
data class ZonesAnalytics(
    val currentRequests: Long,
    val currentCached: Long,
    val previousRequests: Long?,
    val previousCached: Long?,
    val hourly: List<ZoneHourRow>,
    val requestsByZone: Map<String, Long>,
    val zonesQueried: Int,
    val zonesTotal: Int,
    /** True when a zone query failed or a zone hit [MAX_HOURLY_ROWS], so totals cover only part of the data. */
    val partial: Boolean = false,
)

/** Why one analytics source could not be shown. Mapped from the API error by [AnalyticsIssueClassifier]. */
sealed interface AnalyticsIssue {
    data class MissingPermission(val permission: String) : AnalyticsIssue
    data object RateLimited : AnalyticsIssue
    data object Failed : AnalyticsIssue

    /** Some of the data was missing or capped, so totals and deltas may be incomplete. */
    data object Partial : AnalyticsIssue
}

enum class MetricKind { COUNT, RATIO, DURATION }

/** A tile's numbers: the current value, the previous equal period (null when absent) and the hourly trend. */
@Immutable
data class Metric(
    val current: Double?,
    val previous: Double?,
    val series: List<Double>,
)

/** Everything the Home analytics section shows. Each source carries its own issue when it failed. */
@Immutable
data class HomeAnalytics(
    val hasComparison: Boolean,
    val workersIssue: AnalyticsIssue?,
    val zonesIssue: AnalyticsIssue?,
    val requests: Metric,
    val cacheHitRate: Metric,
    val invocations: Metric,
    val errors: Metric,
    val cpuP50Ms: Metric,
    val cpuP99Ms: Metric,
    val zonesQueried: Int,
    val zonesTotal: Int,
    /** Requests for every Worker the analytics returned, busiest first. Bounded by the query's script limit. */
    val requestsByWorker: List<ScriptRequests>,
    /** True when the account has more Workers than the query could rank, so some Workers have no count. */
    val workerCountsApproximate: Boolean,
    val requestsByZone: Map<String, Long>,
)
