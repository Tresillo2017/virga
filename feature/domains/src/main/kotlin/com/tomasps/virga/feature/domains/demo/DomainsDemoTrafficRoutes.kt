package com.tomasps.virga.feature.domains.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sin

/** Operation name of the zone traffic query in ZoneTrafficRepository. */
internal const val ZONE_TRAFFIC_OPERATION = "ZoneTraffic"

private const val MAX_SERIES_HOURS = 30 * 24
private const val BASE_REQUESTS_PER_HOUR = 1_200.0
private const val WAVE_AMPLITUDE = 0.4
private const val WAVE_STEP = 3.0
private const val CACHED_SHARE = 0.72
private const val BYTES_PER_REQUEST = 48_000L
private const val UNIQUES_SHARE = 0.18
private const val ZONE_SCALE_BUCKETS = 5
private const val ZONE_SCALE_STEP = 0.35
private const val TRAFFIC_ERROR = "Demo analytics request was not understood"

/**
 * Serves the zone traffic GraphQL query for any zone id. Each zone gets a steady scale from its id, so the
 * overview tiles and chart look different per zone; the window comes from the request's start and end.
 */
internal fun DemoRouteRegistry.trafficRoutes() {
    graphQl(ZONE_TRAFFIC_OPERATION) { request -> trafficReply(request) }
}

internal fun trafficReply(request: DemoRequest): DemoReply {
    val variables = requestVariables(request) ?: return DemoReply.graphQlErrors(TRAFFIC_ERROR)
    val zoneTag = variables["zoneTag"]?.jsonPrimitive?.content ?: return DemoReply.graphQlErrors(TRAFFIC_ERROR)
    val start = variables.instant("start") ?: return DemoReply.graphQlErrors(TRAFFIC_ERROR)
    val end = variables.instant("end") ?: return DemoReply.graphQlErrors(TRAFFIC_ERROR)
    val hours = ChronoUnit.HOURS.between(start, end).coerceIn(1L, MAX_SERIES_HOURS.toLong()).toInt()
    val scale = zoneScale(zoneTag)
    val series = buildJsonArray {
        for (index in 0 until hours) {
            val at = start.plus(Duration.ofHours(index.toLong()))
            val requests = requestsAt(index, scale)
            add(seriesRow(at, requests))
        }
    }
    val totalRequests = (0 until hours).sumOf { requestsAt(it, scale).toLong() }
    val zone = buildJsonObject {
        put("totals", buildJsonArray { add(totalsRow(totalRequests)) })
        put("series", series)
    }
    return DemoReply.graphQl(
        buildJsonObject {
            put("viewer", buildJsonObject { put("zones", JsonArray(listOf(zone))) })
        },
    )
}

private fun requestVariables(request: DemoRequest): JsonObject? {
    val body = request.bodyText ?: return null
    return runCatching {
        Json.parseToJsonElement(body).jsonObject["variables"]?.jsonObject
    }.getOrNull()
}

private fun JsonObject.instant(key: String): Instant? =
    runCatching { Instant.parse(this[key]?.jsonPrimitive?.content.orEmpty()) }.getOrNull()

/** A stable multiplier in 1.0..(1 + bucket step) chosen from the zone id, so zones differ but never go to zero. */
internal fun zoneScale(zoneTag: String): Double {
    val bucket = abs(zoneTag.hashCode()) % ZONE_SCALE_BUCKETS
    return 1.0 + bucket * ZONE_SCALE_STEP
}

private fun requestsAt(index: Int, scale: Double): Double =
    BASE_REQUESTS_PER_HOUR * scale * (1.0 + WAVE_AMPLITUDE * sin(index / WAVE_STEP))

private fun seriesRow(at: Instant, requests: Double): JsonObject {
    val total = requests.toLong()
    val cached = (requests * CACHED_SHARE).toLong()
    return buildJsonObject {
        put("dimensions", buildJsonObject { put("datetime", at.toString()) })
        put("sum", buildJsonObject {
            put("requests", total)
            put("cachedRequests", cached)
        })
    }
}

private fun totalsRow(totalRequests: Long): JsonObject = buildJsonObject {
    put("uniq", buildJsonObject { put("uniques", (totalRequests * UNIQUES_SHARE).toLong()) })
    put("sum", buildJsonObject {
        put("requests", totalRequests)
        put("cachedRequests", (totalRequests * CACHED_SHARE).toLong())
        put("bytes", totalRequests * BYTES_PER_REQUEST)
    })
}
