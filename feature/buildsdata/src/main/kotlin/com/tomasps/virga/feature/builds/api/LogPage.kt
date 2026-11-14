package com.tomasps.virga.feature.builds.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One page of build log lines and the cursor for the next read. */
data class LogPage(val lines: List<String>, val cursor: String?, val truncated: Boolean = false)

private val TEXT_KEYS = listOf("message", "text", "line")

/**
 * Reads the log `result`. The reference says `result` is `{ cursor?, lines?, truncated? }` and that each line is
 * an array of `[unix timestamp, message]`. The message is the last string in the array. Bare strings and objects
 * with a message-like key are also accepted, so one odd line does not hide the rest. Anything else gives no lines.
 */
internal fun parseLogPage(element: JsonElement?): LogPage = when (element) {
    is JsonObject -> LogPage(
        lines = (element["lines"] as? JsonArray).orEmpty().mapNotNull(::lineText),
        cursor = cursorText(element["cursor"]),
        truncated = (element["truncated"] as? JsonPrimitive)?.contentOrNull == "true",
    )
    else -> LogPage(emptyList(), cursor = null)
}

private fun lineText(element: JsonElement): String? = when (element) {
    is JsonArray -> element.lastOrNull { it is JsonPrimitive && !it.isNumber() }
        ?.let { (it as JsonPrimitive).contentOrNull }
    is JsonPrimitive -> element.contentOrNull
    is JsonObject -> TEXT_KEYS.firstNotNullOfOrNull { key -> (element[key] as? JsonPrimitive)?.contentOrNull }
}

private fun JsonPrimitive.isNumber(): Boolean = !isString && contentOrNull?.toDoubleOrNull() != null

private fun cursorText(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
