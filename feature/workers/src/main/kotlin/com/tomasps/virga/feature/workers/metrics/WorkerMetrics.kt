package com.tomasps.virga.feature.workers.metrics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** One time bucket of a Worker's invocations, with every status code merged into it. */
data class MetricsBucket(
    val time: String,
    val requests: Double,
    val errors: Double,
    val subrequests: Double,
    /** Highest P50 CPU time across the status rows of this bucket. Quantiles cannot be merged exactly. */
    val cpuP50Us: Double?,
    /** Highest P99 CPU time across the status rows of this bucket. */
    val cpuP99Us: Double?,
)

data class StatusCount(val status: String, val requests: Double)

/** Metrics for one Worker over the chosen window, from workersInvocationsAdaptive. */
data class WorkerMetrics(
    val buckets: List<MetricsBucket>,
    val statuses: List<StatusCount>,
    /** True when the result filled the row limit, so totals may be missing rows. */
    val isTruncated: Boolean = false,
) {
    val totalRequests: Double get() = buckets.sumOf { it.requests }
    val totalErrors: Double get() = buckets.sumOf { it.errors }
    val totalSubrequests: Double get() = buckets.sumOf { it.subrequests }
}

/** Turns the raw GraphQL data object into [WorkerMetrics]. Malformed rows are skipped, never thrown. */
internal object WorkerMetricsParser {

    /** [rowLimit] is the limit the query asked for. A result that fills it is flagged as truncated. */
    fun parse(root: JsonObject, rowLimit: Int? = null): WorkerMetrics {
        val rawRows = rowsOf(root)
        val rows = rawRows.mapNotNull(::rowOf)
        return WorkerMetrics(
            buckets = bucketsOf(rows),
            statuses = statusesOf(rows),
            isTruncated = rowLimit != null && rawRows.size >= rowLimit,
        )
    }

    private fun rowsOf(root: JsonObject): List<JsonObject> {
        val viewer = root["viewer"] as? JsonObject ?: return emptyList()
        val account = (viewer["accounts"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return emptyList()
        return (account[DATASET] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    }

    private fun rowOf(obj: JsonObject): Row? {
        val dimensions = obj["dimensions"] as? JsonObject ?: return null
        val time = text(dimensions["datetime"]) ?: return null
        val sum = obj["sum"] as? JsonObject
        val quantiles = obj["quantiles"] as? JsonObject
        return Row(
            time = time,
            status = text(dimensions["status"]) ?: UNKNOWN_STATUS,
            requests = number(sum?.get("requests")),
            errors = number(sum?.get("errors")),
            subrequests = number(sum?.get("subrequests")),
            cpuP50 = (quantiles?.get("cpuTimeP50") as? JsonPrimitive)?.doubleOrNull,
            cpuP99 = (quantiles?.get("cpuTimeP99") as? JsonPrimitive)?.doubleOrNull,
        )
    }

    private fun bucketsOf(rows: List<Row>): List<MetricsBucket> = rows
        .groupBy { it.time }
        .map { (time, group) ->
            MetricsBucket(
                time = time,
                requests = group.sumOf { it.requests },
                errors = group.sumOf { it.errors },
                subrequests = group.sumOf { it.subrequests },
                cpuP50Us = group.mapNotNull { it.cpuP50 }.maxOrNull(),
                cpuP99Us = group.mapNotNull { it.cpuP99 }.maxOrNull(),
            )
        }
        .sortedBy { it.time }

    private fun statusesOf(rows: List<Row>): List<StatusCount> = rows
        .groupBy { it.status }
        .map { (status, group) -> StatusCount(status, group.sumOf { it.requests }) }
        .sortedByDescending { it.requests }

    private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

    private fun number(element: JsonElement?): Double = (element as? JsonPrimitive)?.doubleOrNull ?: 0.0

    private class Row(
        val time: String,
        val status: String,
        val requests: Double,
        val errors: Double,
        val subrequests: Double,
        val cpuP50: Double?,
        val cpuP99: Double?,
    )

    private const val DATASET = "workersInvocationsAdaptive"
    private const val UNKNOWN_STATUS = "unknown"
}
