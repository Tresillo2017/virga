package com.tomasps.virga.feature.d1.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.graphQlAccountData
import com.tomasps.virga.core.network.demo.graphQlVariables
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sin

/** Operation names the D1 metrics queries send. Registered with the registry by operation name. */
internal const val D1_DAILY_OPERATION = "D1Daily"
internal const val D1_STORAGE_OPERATION = "D1Storage"

private const val DAILY_DATASET = "d1AnalyticsAdaptiveGroups"
private const val STORAGE_DATASET = "d1StorageAdaptiveGroups"
private const val MESSAGE = "Demo metrics need a valid window"
private const val MAX_DAYS = 90L
private const val BASE_READS = 1_400L
private const val BASE_WRITES = 180L
private const val ROWS_PER_READ = 6L
private const val ROWS_PER_WRITE = 2L
private const val BASE_DB_BYTES = 9_000_000L
private const val BYTES_PER_DAY = 350_000L
private const val BASE_LATENCY_MS = 4.2
private const val LATENCY_WAVE_MS = 1.6
private const val WAVE_STEP = 2.0
private const val SCALE_SLOTS = 255
private const val SCALE_FLOOR = 0.5

/** Canned daily D1 analytics for one database: reads, writes, rows and p90 latency. */
internal fun d1DailyReply(request: DemoRequest): DemoReply {
    val window = window(request) ?: return DemoReply.graphQlErrors(MESSAGE)
    val scale = databaseScale(window.databaseId)
    val rows = buildJsonArray {
        for (index in 0 until window.days) {
            val reads = ((BASE_READS + index * 23L) * scale).toLong()
            val writes = ((BASE_WRITES + index * 5L) * scale).toLong()
            add(buildJsonObject {
                put("sum", buildJsonObject {
                    put("readQueries", reads)
                    put("writeQueries", writes)
                    put("rowsRead", reads * ROWS_PER_READ)
                    put("rowsWritten", writes * ROWS_PER_WRITE)
                })
                put("quantiles", buildJsonObject {
                    put("queryBatchTimeMsP90", BASE_LATENCY_MS + LATENCY_WAVE_MS * sin(index / WAVE_STEP))
                })
                put("dimensions", buildJsonObject { put("date", window.start.plusDays(index.toLong()).toString()) })
            })
        }
    }
    return DemoReply.graphQl(graphQlAccountData(mapOf(DAILY_DATASET to rows)))
}

/** Canned database size per day, in bytes. The screen reads the largest value in the window. */
internal fun d1StorageReply(request: DemoRequest): DemoReply {
    val window = window(request) ?: return DemoReply.graphQlErrors(MESSAGE)
    val scale = databaseScale(window.databaseId)
    val rows = buildJsonArray {
        for (index in 0 until window.days) {
            add(buildJsonObject {
                put("max", buildJsonObject {
                    put("databaseSizeBytes", ((BASE_DB_BYTES + BYTES_PER_DAY * index) * scale).toLong())
                })
                put("dimensions", buildJsonObject { put("date", window.start.plusDays(index.toLong()).toString()) })
            })
        }
    }
    return DemoReply.graphQl(graphQlAccountData(mapOf(STORAGE_DATASET to rows)))
}

private data class DemoWindow(val databaseId: String, val start: LocalDate, val days: Int)

private fun window(request: DemoRequest): DemoWindow? {
    val variables = request.graphQlVariables() ?: return null
    val start = variables.date("start") ?: return null
    val end = variables.date("end") ?: return null
    val days = ChronoUnit.DAYS.between(start, end).coerceIn(1L, MAX_DAYS).toInt() + 1
    return DemoWindow(variables["databaseId"]?.jsonPrimitive?.content.orEmpty(), start, days)
}

/** Stable per-database multiplier so two databases show different numbers. */
private fun databaseScale(databaseId: String): Double =
    SCALE_FLOOR + (abs(databaseId.hashCode()) % SCALE_SLOTS) / SCALE_SLOTS.toDouble()

private fun kotlinx.serialization.json.JsonObject.date(name: String): LocalDate? =
    runCatching { LocalDate.parse(this[name]?.jsonPrimitive?.content.orEmpty()) }.getOrNull()
