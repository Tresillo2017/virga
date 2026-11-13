package com.tomasps.virga.feature.domains.analytics

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.core.network.GraphQlClient
import com.tomasps.virga.core.ui.components.TimeRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Whole-window totals plus an hourly series for the chart. [percentCached] is null when there were no requests. */
data class ZoneTraffic(
    val uniques: Long,
    val requests: Long,
    val cachedRequests: Long,
    val bytes: Long,
    val series: List<TrafficPoint>,
) {
    val percentCached: Double?
        get() = if (requests > 0) cachedRequests * PERCENT / requests.toDouble() else null

    private companion object {
        const val PERCENT = 100.0
    }
}

data class TrafficPoint(val datetime: String, val requests: Long, val cachedRequests: Long)

/** Reads the hourly HTTP dataset for one zone. The dataset keeps per-plan retention, which the time range picker respects. */
class ZoneTrafficRepository(private val graphQl: GraphQlClient) {

    suspend fun traffic(zoneId: String, range: TimeRange, now: Instant = Instant.now()): ApiResult<ZoneTraffic> {
        val start = now.minus(range.hours.toLong(), ChronoUnit.HOURS)
        val variables = buildJsonObject {
            put("zoneTag", zoneId)
            put("start", start.toString())
            put("end", now.toString())
        }
        return graphQl.query(TRAFFIC_QUERY, variables, TrafficData.serializer()).map { data -> data.toTraffic() }
    }
}

@Serializable
data class TrafficData(val viewer: Viewer = Viewer()) {
    fun toTraffic(): ZoneTraffic {
        val zone = viewer.zones.firstOrNull() ?: return ZoneTraffic(0, 0, 0, 0, emptyList())
        val totals = zone.totals.firstOrNull()
        return ZoneTraffic(
            uniques = totals?.uniq?.uniques ?: 0,
            requests = totals?.sum?.requests ?: 0,
            cachedRequests = totals?.sum?.cachedRequests ?: 0,
            bytes = totals?.sum?.bytes ?: 0,
            series = zone.series.map { group ->
                TrafficPoint(
                    datetime = group.dimensions?.datetime.orEmpty(),
                    requests = group.sum?.requests ?: 0,
                    cachedRequests = group.sum?.cachedRequests ?: 0,
                )
            },
        )
    }
}

@Serializable
data class Viewer(val zones: List<ZoneNode> = emptyList())

@Serializable
data class ZoneNode(
    val totals: List<Group> = emptyList(),
    val series: List<Group> = emptyList(),
)

@Serializable
data class Group(
    val sum: Sums? = null,
    val uniq: Uniq? = null,
    val dimensions: Dimensions? = null,
)

@Serializable
data class Sums(
    val requests: Long = 0,
    val cachedRequests: Long = 0,
    val bytes: Long = 0,
)

@Serializable
data class Uniq(val uniques: Long = 0)

@Serializable
data class Dimensions(val datetime: String? = null)

/**
 * Totals are one group with no dimensions, so the whole window aggregates into it. The series groups by hour.
 * Field names follow the httpRequests1hGroups dataset; they were not re-read from the schema, see the report.
 */
private const val TRAFFIC_QUERY = """
query ZoneTraffic(${'$'}zoneTag: string, ${'$'}start: Time, ${'$'}end: Time) {
  viewer {
    zones(filter: { zoneTag: ${'$'}zoneTag }) {
      totals: httpRequests1hGroups(limit: 1, filter: { datetime_geq: ${'$'}start, datetime_lt: ${'$'}end }) {
        uniq { uniques }
        sum { requests cachedRequests bytes }
      }
      series: httpRequests1hGroups(
        limit: 1000
        orderBy: [datetime_ASC]
        filter: { datetime_geq: ${'$'}start, datetime_lt: ${'$'}end }
      ) {
        dimensions { datetime }
        sum { requests cachedRequests }
      }
    }
  }
}
"""
