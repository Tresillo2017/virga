package com.tomasps.virga.feature.workers.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.sin

/** The operation name the Workers metrics query sends. Registered with the registry by operation name. */
internal const val WORKER_INVOCATIONS_OPERATION = "GetWorkerInvocations"

private const val MAX_HOURS = 744
private const val OK_STATUS = "200"
private const val ERROR_STATUS = "500"
private const val ERROR_SHARE = 0.02
private const val BASE_REQUESTS_PER_HOUR = 850.0
private const val WAVE_AMPLITUDE = 0.4
private const val WAVE_STEP = 3.0
private const val CPU_P50_MICROS = 2_100.0
private const val CPU_P99_MICROS = 18_500.0
private const val SCRIPT_SCALE_FLOOR = 0.5
private const val SCRIPT_SCALE_BUCKETS = 255
private const val DEMO_ERROR_MESSAGE = "Demo metrics need a valid window"

/**
 * Canned hourly invocations for one Worker, shaped as workersInvocationsAdaptive rows (one per hour and status).
 * The script name and window come from the query variables, so each Worker shows its own numbers.
 */
internal fun workerInvocationsReply(request: DemoRequest): DemoReply {
    val variables = request.bodyText?.let { parseVariables(it) } ?: return DemoReply.graphQlErrors(DEMO_ERROR_MESSAGE)
    val scriptName = variables["scriptName"]?.jsonPrimitive?.content.orEmpty()
    val start = variables.instant("datetimeStart") ?: return DemoReply.graphQlErrors(DEMO_ERROR_MESSAGE)
    val end = variables.instant("datetimeEnd") ?: return DemoReply.graphQlErrors(DEMO_ERROR_MESSAGE)
    val hours = ChronoUnit.HOURS.between(start, end).coerceIn(1L, MAX_HOURS.toLong()).toInt()
    val scale = scriptScale(scriptName)
    val rows = buildJsonArray {
        for (index in 0 until hours) {
            val at = start.plus(Duration.ofHours(index.toLong()))
            val requests = BASE_REQUESTS_PER_HOUR * scale * (1.0 + WAVE_AMPLITUDE * sin(index / WAVE_STEP))
            add(row(at, scriptName, OK_STATUS, requests * (1 - ERROR_SHARE)))
            add(row(at, scriptName, ERROR_STATUS, requests * ERROR_SHARE))
        }
    }
    return DemoReply.graphQl(
        buildJsonObject {
            put("viewer", buildJsonObject {
                put("accounts", buildJsonArray {
                    add(buildJsonObject { put("workersInvocationsAdaptive", rows) })
                })
            })
        },
    )
}

/** Stable per-Worker multiplier, so each Worker shows its own volume without randomness. */
internal fun scriptScale(scriptName: String): Double =
    SCRIPT_SCALE_FLOOR + (kotlin.math.abs(scriptName.hashCode()) % SCRIPT_SCALE_BUCKETS) / SCRIPT_SCALE_BUCKETS.toDouble()

private fun row(at: Instant, scriptName: String, status: String, requests: Double) = buildJsonObject {
    put("dimensions", buildJsonObject {
        put("datetime", at.toString())
        put("scriptName", scriptName)
        put("status", status)
    })
    put("sum", buildJsonObject {
        put("requests", requests.toLong())
        put("errors", if (status == ERROR_STATUS) requests.toLong() else 0L)
        put("subrequests", (requests * 0.5).toLong())
    })
    put("quantiles", buildJsonObject {
        put("cpuTimeP50", CPU_P50_MICROS)
        put("cpuTimeP99", CPU_P99_MICROS)
    })
}

private fun parseVariables(body: String): JsonObject? = runCatching {
    kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["variables"]?.jsonObject
}.getOrNull()

private fun JsonObject.instant(name: String): Instant? =
    runCatching { Instant.parse(this[name]?.jsonPrimitive?.content.orEmpty()) }.getOrNull()
