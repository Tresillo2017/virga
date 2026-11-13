package com.tomasps.virga.feature.pages.demo

import com.tomasps.virga.feature.pages.logic.PagesStageName
import com.tomasps.virga.feature.pages.logic.PagesStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Small JSON helpers for the demo stores. They mirror what the API does with a PATCH: nested merge, null removes. */

internal fun jsonStrings(vararg values: String): JsonArray = JsonArray(values.map { JsonPrimitive(it) })

internal fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

internal fun parseObject(text: String): JsonObject? = runCatching {
    Json.parseToJsonElement(text).jsonObject
}.getOrNull()

/** Merges [patch] into [base] recursively. A JSON null in the patch removes the key. */
internal fun deepMerge(base: JsonObject, patch: JsonObject): JsonObject {
    val merged = LinkedHashMap<String, JsonElement>(base)
    patch.forEach { (key, value) ->
        val current = merged[key]
        when {
            value is JsonNull -> merged.remove(key)
            value is JsonObject && current is JsonObject -> merged[key] = deepMerge(current, value)
            else -> merged[key] = value
        }
    }
    return JsonObject(merged)
}

internal fun JsonObject.withStages(stages: JsonArray): JsonObject =
    JsonObject(this + ("stages" to stages) + ("latest_stage" to latestStageOf(stages)))

internal fun JsonObject.latestStatus(): String =
    (this["latest_stage"] as? JsonObject)?.string("status").orEmpty()

/** Each stage of a deployment, in pipeline order, with the given status for each name. */
internal fun pipelineStages(statusFor: (String) -> String): JsonArray = JsonArray(
    PagesStageName.PIPELINE.map { name ->
        stageObject(name, statusFor(name))
    },
)

/** Moves an active stage on to success and activates the next idle one. The last stage finishing completes the deploy. */
internal fun nextStageStates(stages: JsonArray): JsonArray {
    val activeIndex = PagesStageName.PIPELINE.indexOfFirst { name ->
        stages.map { it as JsonObject }.firstOrNull { it.string("name") == name }?.string("status") == PagesStatus.ACTIVE
    }
    if (activeIndex < 0) return stages
    val statusFor: (String) -> String = { name ->
        val index = PagesStageName.PIPELINE.indexOf(name)
        when {
            index <= activeIndex -> PagesStatus.SUCCESS
            index == activeIndex + 1 -> PagesStatus.ACTIVE
            else -> PagesStatus.IDLE
        }
    }
    return pipelineStages(statusFor)
}

private fun stageObject(name: String, status: String): JsonObject = JsonObject(
    mapOf(
        "name" to JsonPrimitive(name),
        "status" to JsonPrimitive(status),
        "started_on" to JsonPrimitive(DEMO_STAGE_START),
        "ended_on" to JsonPrimitive(if (status == PagesStatus.ACTIVE || status == PagesStatus.IDLE) "" else DEMO_STAGE_END),
    ),
)

private fun latestStageOf(stages: JsonArray): JsonObject {
    val started = stages.map { it as JsonObject }.lastOrNull { it.string("status") != PagesStatus.IDLE }
    return started ?: stages.first() as JsonObject
}

private const val DEMO_STAGE_START = "2026-11-09T14:00:00Z"
private const val DEMO_STAGE_END = "2026-11-09T14:01:00Z"
