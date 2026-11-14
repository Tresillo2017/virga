package com.tomasps.virga.feature.builds.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * A build trigger of a Worker. The reference types the list `result` as unknown, so every field is read
 * defensively. [triggerUuid] is the only key the build-start call needs.
 */
data class TriggerRef(val triggerUuid: String, val name: String?, val branchIncludes: List<String>)

/** What a manual build starts from: a branch tip or one commit. The API requires one of the two. */
sealed interface BuildTarget {
    data class Branch(val branch: String) : BuildTarget
    data class Commit(val commitHash: String) : BuildTarget

    fun toBody(): JsonObject = when (this) {
        is Branch -> buildJsonObject { put("branch", branch) }
        is Commit -> buildJsonObject { put("commit_hash", commitHash) }
    }
}

/** Reads the trigger list from either a bare array or an object with a `triggers` array. Skips malformed items. */
internal fun parseTriggers(element: JsonElement?): List<TriggerRef> {
    val items = when (element) {
        is JsonArray -> element
        is JsonObject -> element["triggers"] as? JsonArray ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }
    return items.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val uuid = obj.text("trigger_uuid")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        TriggerRef(
            triggerUuid = uuid,
            name = obj.text("trigger_name")?.takeIf { it.isNotBlank() },
            branchIncludes = (obj["branch_includes"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        )
    }
}

/** The build UUID from a start-build response, or null when the response carries none. */
internal fun parseStartedBuildUuid(element: JsonElement?): String? =
    (element as? JsonObject)?.text("build_uuid")?.takeIf { it.isNotBlank() }

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
