package com.tomasps.virga.feature.r2.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.graphQlAccountData
import com.tomasps.virga.core.network.demo.graphQlVariables
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sin

/** The operation name the R2 bucket metrics query sends. Registered with the registry by operation name. */
internal const val R2_BUCKET_METRICS_OPERATION = "R2BucketMetrics"

private const val MAX_DAYS = 90L
private const val MESSAGE = "Demo metrics need a valid window"
private const val DAILY_REQUESTS_PER_UNIT = 240.0
private const val BASE_OBJECTS = 1_200L
private const val OBJECTS_PER_DAY = 35L
private const val BASE_PAYLOAD_BYTES = 180_000_000L
private const val PAYLOAD_PER_DAY_BYTES = 4_000_000L
private const val WAVE_AMPLITUDE = 0.35
private const val WAVE_STEP = 2.5
private const val SCALE_BUCKETS = 255.0
private const val SCALE_FLOOR = 0.5

/** Relative request weight per action type, as the r2 operations dataset reports them. */
private val ACTION_WEIGHTS = listOf(
    "GetObject" to 1.0,
    "PutObject" to 0.35,
    "ListBucket" to 0.2,
    "HeadObject" to 0.12,
    "DeleteObject" to 0.05,
)

/** Canned operations and daily storage for one bucket, shaped like the r2 adaptive datasets. */
internal fun r2BucketMetricsReply(request: DemoRequest): DemoReply {
    val variables = request.graphQlVariables() ?: return DemoReply.graphQlErrors(MESSAGE)
    val start = variables.instant("startDate") ?: return DemoReply.graphQlErrors(MESSAGE)
    val end = variables.instant("endDate") ?: return DemoReply.graphQlErrors(MESSAGE)
    val days = ChronoUnit.DAYS.between(start, end).coerceIn(1L, MAX_DAYS).toInt()
    val scale = bucketScale(variables["bucketName"]?.jsonPrimitive?.content.orEmpty())
    return DemoReply.graphQl(
        graphQlAccountData(
            mapOf(
                OPERATIONS_DATASET to operationRows(days, scale),
                STORAGE_DATASET to storageRows(start, days, scale),
            ),
        ),
    )
}

private const val OPERATIONS_DATASET = "r2OperationsAdaptiveGroups"
private const val STORAGE_DATASET = "r2StorageAdaptiveGroups"

private fun operationRows(days: Int, scale: Double): JsonArray = buildJsonArray {
    for ((action, weight) in ACTION_WEIGHTS) {
        add(buildJsonObject {
            put("sum", buildJsonObject { put("requests", (weight * DAILY_REQUESTS_PER_UNIT * days * scale).toLong()) })
            put("dimensions", buildJsonObject { put("actionType", action) })
        })
    }
}

private fun storageRows(start: Instant, days: Int, scale: Double): JsonArray = buildJsonArray {
    for (index in 0 until days) {
        val at = start.plus(Duration.ofDays(index.toLong()))
        val wave = 1.0 + WAVE_AMPLITUDE * sin(index / WAVE_STEP)
        add(buildJsonObject {
            put("max", buildJsonObject {
                put("objectCount", ((BASE_OBJECTS + OBJECTS_PER_DAY * index) * scale).toLong())
                put("payloadSize", ((BASE_PAYLOAD_BYTES + PAYLOAD_PER_DAY_BYTES * index * wave) * scale).toLong())
            })
            put("dimensions", buildJsonObject { put("datetime", at.toString()) })
        })
    }
}

/** Stable per-bucket multiplier, so each bucket shows different numbers without randomness. */
private fun bucketScale(bucketName: String): Double =
    SCALE_FLOOR + (abs(bucketName.hashCode()) % SCALE_BUCKETS.toInt()) / SCALE_BUCKETS

private fun kotlinx.serialization.json.JsonObject.instant(name: String): Instant? =
    runCatching { Instant.parse(this[name]?.jsonPrimitive?.content.orEmpty()) }.getOrNull()
