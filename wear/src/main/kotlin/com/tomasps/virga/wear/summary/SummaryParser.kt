package com.tomasps.virga.wear.summary

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Requests and errors over the last 24 hours. */
data class SummaryData(val requests: Long, val errors: Long)

/**
 * Reads the account node of [data]. Only a well-formed response yields a value; anything else returns null, so a
 * drifted schema or a null field becomes a failure (and the last good numbers stay) instead of a confident zero.
 * Well-formed means: a `viewer`, a non-empty `accounts` whose first node has a `totals` list, and every row has a
 * `sum` object with whole-number `requests` and `errors`. An empty `totals` list is how no traffic looks, a real zero.
 * Counts are read with `longOrNull`, as Home's parser does, which also accepts a numeric string.
 */
internal fun parseSummary(data: JsonObject?): SummaryData? {
    val viewer = data?.get("viewer") as? JsonObject ?: return null
    val account = (viewer["accounts"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
    val rows = account["totals"] as? JsonArray ?: return null
    var requests = 0L
    var errors = 0L
    for (row in rows) {
        val sum = (row as? JsonObject)?.get("sum") as? JsonObject ?: return null
        requests += sum.count("requests") ?: return null
        errors += sum.count("errors") ?: return null
    }
    return SummaryData(requests, errors)
}

private fun JsonObject.count(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
