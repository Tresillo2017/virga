package com.tomasps.virga.core.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds envelope bodies in the shape the real API returns. Used by demo routes and test fixtures. */
object CloudflareEnvelopes {
    fun success(result: JsonElement, resultInfo: JsonElement? = null): String = buildJsonObject {
        put("success", true)
        put("errors", buildJsonArray { })
        put("messages", buildJsonArray { })
        put("result", result)
        if (resultInfo != null) put("result_info", resultInfo)
    }.toString()

    fun failure(code: Int, message: String): String = buildJsonObject {
        put("success", false)
        put("errors", buildJsonArray { add(errorEntry(code, message)) })
        put("messages", buildJsonArray { })
        put("result", JsonNull)
    }.toString()

    private fun errorEntry(code: Int, message: String): JsonObject = buildJsonObject {
        put("code", code)
        put("message", message)
    }
}
