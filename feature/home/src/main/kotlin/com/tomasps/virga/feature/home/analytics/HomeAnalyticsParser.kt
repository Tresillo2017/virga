package com.tomasps.virga.feature.home.analytics

import kotlinx.serialization.json.JsonObject
import java.time.Instant

/** Turns the Workers account node into [WorkersAnalytics], splitting the hourly rows at [AnalyticsWindow.currentStart]. */
internal fun parseWorkersAnalytics(data: JsonObject?, window: AnalyticsWindow): WorkersAnalytics {
    val node = accountNode(data)
    val rawHourly = node?.objectList("hourly").orEmpty()
    val rows = rawHourly.mapNotNull { row ->
        val at = row.rowInstant() ?: return@mapNotNull null
        val sum = row.objectOrNull("sum")
        WorkerHourRow(
            at = hourBucket(at),
            requests = sum?.longOrZero("requests") ?: 0L,
            errors = sum?.longOrZero("errors") ?: 0L,
        )
    }
    val current = rows.filter { it.at >= window.currentStart }.foldHourly()
    val previous = if (window.hasComparison) rows.filter { it.at < window.currentStart }.foldHourly() else null
    return WorkersAnalytics(
        currentRequests = current.requests,
        currentErrors = current.errors,
        previousRequests = previous?.requests,
        previousErrors = previous?.errors,
        currentCpu = node?.quantilesOrNull("currentCpu") ?: CpuQuantiles(null, null),
        previousCpu = if (window.hasComparison) node?.quantilesOrNull("previousCpu") ?: CpuQuantiles(null, null) else null,
        hourly = current.hourly,
        byScript = node?.parseByScript().orEmpty(),
        totalsTruncated = rawHourly.size >= MAX_HOURLY_ROWS,
        topTruncated = node?.objectList("byScript").orEmpty().size >= TOP_SCRIPT_LIMIT,
    )
}

private data class Folded(val requests: Long, val errors: Long, val hourly: List<WorkerHourRow>)

private fun List<WorkerHourRow>.foldHourly(): Folded {
    val byHour = linkedMapOf<Instant, WorkerHourRow>()
    forEach { row ->
        val existing = byHour[row.at]
        byHour[row.at] = if (existing == null) row else row.copy(
            requests = existing.requests + row.requests,
            errors = existing.errors + row.errors,
        )
    }
    val hourly = byHour.values.sortedBy { it.at }
    return Folded(
        requests = hourly.sumOf { it.requests },
        errors = hourly.sumOf { it.errors },
        hourly = hourly,
    )
}

private fun JsonObject.parseByScript(): List<ScriptRequests> = objectList("byScript").mapNotNull { row ->
    val name = row.objectOrNull("dimensions")?.stringOrNull("scriptName") ?: return@mapNotNull null
    ScriptRequests(scriptName = name, requests = row.objectOrNull("sum")?.longOrZero("requests") ?: 0L)
}.groupBy { it.scriptName }
    .map { (name, rows) -> ScriptRequests(name, rows.sumOf { it.requests }) }
    .sortedByDescending { it.requests }

/**
 * Turns the zones node list into [ZonesAnalytics]. [zonesTotal] is the number of zones the account has; [zonesQueried]
 * is how many of them the caller asked for. Zones without a parseable zoneTag still count in the totals.
 */
internal fun parseZonesAnalytics(
    responses: List<JsonObject?>,
    window: AnalyticsWindow,
    zonesQueried: Int,
    zonesTotal: Int,
    chunkFailures: Int = 0,
): ZonesAnalytics {
    val zoneRows = responses.flatMap { data -> accountNode(data)?.objectList("zones").orEmpty() }
    val anyZoneCapped = zoneRows.any { zone -> zone.objectList("hourly").size >= MAX_HOURLY_ROWS }
    val hourlyRows = zoneRows.flatMap { zone ->
        zone.objectList("hourly").mapNotNull { row ->
            val at = row.rowInstant() ?: return@mapNotNull null
            val sum = row.objectOrNull("sum")
            Triple(at, sum?.longOrZero("requests") ?: 0L, sum?.longOrZero("cachedRequests") ?: 0L)
        }
    }
    val perZone = zoneRows.mapNotNull { zone ->
        val tag = zone.stringOrNull("zoneTag") ?: return@mapNotNull null
        val requests = zone.objectList("hourly")
            .filter { row -> row.rowInstant()?.let { it >= window.currentStart } == true }
            .sumOf { row -> row.objectOrNull("sum")?.longOrZero("requests") ?: 0L }
        tag to requests
    }.toMap()
    val rows = hourlyRows.map { (at, requests, cached) -> ZoneHourRow(hourBucket(at), requests, cached) }
    val current = rows.filter { it.at >= window.currentStart }.groupHourly()
    val previous = if (window.hasComparison) rows.filter { it.at < window.currentStart }.groupHourly() else null
    return ZonesAnalytics(
        currentRequests = current.sumOf { it.requests },
        currentCached = current.sumOf { it.cached },
        previousRequests = previous?.sumOf { it.requests },
        previousCached = previous?.sumOf { it.cached },
        hourly = current,
        requestsByZone = perZone,
        zonesQueried = zonesQueried,
        zonesTotal = zonesTotal,
        partial = chunkFailures > 0 || anyZoneCapped,
    )
}

private fun List<ZoneHourRow>.groupHourly(): List<ZoneHourRow> = groupBy { it.at }
    .map { (at, rows) -> ZoneHourRow(at, rows.sumOf { it.requests }, rows.sumOf { it.cached }) }
    .sortedBy { it.at }
