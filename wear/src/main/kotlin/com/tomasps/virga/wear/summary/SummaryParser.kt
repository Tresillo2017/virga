package com.tomasps.virga.wear.summary

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Requests and errors over the last 24 hours. */
data class SummaryData(val requests: Long, val errors: Long)

/**
 * Reads the account node of [data]. Returns null only when the response has no `viewer` object (malformed), so a
 * response with no rows, or no account node, is a real zero. Missing or oddly typed counts read as zero.
 */
internal fun parseSummary(data: JsonObject?): SummaryData? {
    val viewer = data?.get("viewer") as? JsonObject ?: return null
    val account = (viewer["accounts"] as? JsonArray)?.firstNotNullOfOrNull { it as? JsonObject }
    val rows = (account?.get("totals") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val sums = rows.map { it["sum"] as? JsonObject }
    return SummaryData(
        requests = sums.sumOf { it.count("requests") },
        errors = sums.sumOf { it.count("errors") },
    )
}

private fun JsonObject?.count(key: String): Long = (this?.get(key) as? JsonPrimitive)?.longOrNull ?: 0L
