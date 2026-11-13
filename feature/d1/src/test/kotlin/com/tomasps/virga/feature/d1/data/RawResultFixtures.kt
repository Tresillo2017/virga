package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.network.demo.DemoReply
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Builds a raw-endpoint reply: one statement result with the given columns and rows. Test-only. */
internal fun rawReply(columns: List<String>, rows: List<List<Any?>>): DemoReply = DemoReply.ok(
    buildJsonArray {
        add(buildJsonObject {
            putJsonObject("results") {
                putJsonArray("columns") { columns.forEach { add(JsonPrimitive(it)) } }
                putJsonArray("rows") {
                    rows.forEach { row -> add(buildJsonArray { row.forEach { add(it.toJson()) } }) }
                }
            }
            putJsonObject("meta") { }
        })
    },
)

internal fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> JsonPrimitive(this.toString())
}

internal fun emptyRawArray(): JsonArray = JsonArray(emptyList())
