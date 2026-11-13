package com.tomasps.virga.feature.home.analytics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/*
 * Defensive readers for GraphQL analytics responses. Missing or oddly typed fields become zero or null,
 * never an exception, because the schema is partly beta and the Home screen must still render.
 */

internal fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.objectList(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.longOrZero(key: String): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

internal fun JsonObject.doubleOrNullField(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }

/** The `data.viewer.accounts[0]` node, or null when the account is missing from the response. */
internal fun accountNode(data: JsonObject?): JsonObject? =
    data?.objectOrNull("viewer")?.objectList("accounts")?.firstOrNull()

/** Parses `datetime` dimensions. Rows without a parseable time are dropped rather than guessed. */
internal fun JsonObject.rowInstant(): Instant? =
    objectOrNull("dimensions")?.stringOrNull("datetime")?.let { text ->
        runCatching { Instant.parse(text) }.getOrNull()
    }

/** An aliased node with limit 1 is a list; its first row carries the `quantiles` object. */
internal fun JsonObject.quantilesOrNull(key: String): CpuQuantiles? {
    val node = objectList(key).firstOrNull()?.objectOrNull("quantiles") ?: return null
    return CpuQuantiles(
        p50Ms = node.doubleOrNullField("cpuTimeP50")?.let(::microsToMillis),
        p99Ms = node.doubleOrNullField("cpuTimeP99")?.let(::microsToMillis),
    )
}

/** Workers CPU quantiles arrive in microseconds. The unit is not confirmed in the reference, so this is noted in the report. */
internal fun microsToMillis(micros: Double): Double = micros / MICROS_PER_MILLI

private const val MICROS_PER_MILLI = 1_000.0
