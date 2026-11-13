package com.tomasps.virga.feature.r2.settings

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put

private const val SECONDS_PER_DAY = 86_400L
private const val MAX_EXPIRY_DAYS = 36_500L
private const val AGE_CONDITION = "Age"

/** The parts of one lifecycle rule that Virga shows and edits. [raw] keeps the whole rule for writing back. */
data class R2LifecycleEntry(
    val id: String,
    val enabled: Boolean,
    val prefix: String,
    val expireAfterDays: Long?,
    val raw: JsonObject,
)

/** Reads one rule from the raw list. Unknown or missing fields give defaults, so odd rules still display. */
fun JsonObject.toLifecycleEntry(): R2LifecycleEntry {
    val condition = (this[TRANSITION_KEY] as? JsonObject)?.get("condition") as? JsonObject
    val maxAgeSeconds = condition?.get("maxAge")?.let { (it as? JsonPrimitive)?.longOrNull }
    val expiry = if (condition?.get("type")?.let { (it as? JsonPrimitive)?.contentOrNull } == AGE_CONDITION) {
        maxAgeSeconds?.let { it / SECONDS_PER_DAY }
    } else {
        null
    }
    return R2LifecycleEntry(
        id = (this["id"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        enabled = (this["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
        prefix = ((this["conditions"] as? JsonObject)?.get("prefix") as? JsonPrimitive)?.contentOrNull.orEmpty(),
        expireAfterDays = expiry,
        raw = this,
    )
}

/**
 * Builds the rule to write. Fields Virga does not edit (for example abortMultipartUploadsTransition or storage
 * class transitions) are copied from [existing], so saving one rule never drops another part of it.
 *
 * When [expiryChanged] is false the original expiry transition is copied unchanged. That keeps a date-based
 * expiry, or an age that is not a whole number of days, instead of rewriting it from the rounded day count.
 */
fun lifecycleRuleFrom(
    id: String,
    enabled: Boolean,
    prefix: String,
    expireAfterDays: Long?,
    existing: JsonObject?,
    expiryChanged: Boolean = true,
): JsonObject {
    val kept = existing?.filterKeys { it !in EDITED_KEYS }.orEmpty()
    val edited = buildJsonObject {
        put("id", id)
        put("enabled", enabled)
        put("conditions", buildJsonObject { put("prefix", prefix) })
    }
    return JsonObject(kept + edited + expiryTransition(existing, expireAfterDays, expiryChanged))
}

/** The expiry entry to write: the original when unchanged, a new age when the user set days, otherwise none. */
private fun expiryTransition(existing: JsonObject?, expireAfterDays: Long?, expiryChanged: Boolean): Map<String, JsonElement> {
    val original = existing?.get(TRANSITION_KEY)
    return when {
        !expiryChanged -> original?.let { mapOf(TRANSITION_KEY to it) }.orEmpty()
        expireAfterDays == null -> emptyMap()
        else -> mapOf(TRANSITION_KEY to buildJsonObject {
            put("condition", buildJsonObject {
                put("type", AGE_CONDITION)
                put("maxAge", expireAfterDays * SECONDS_PER_DAY)
            })
        })
    }
}

/** Expiry days must be a whole number from 1 to the largest value the form accepts. */
fun isInvalidExpiryDays(days: String): Boolean {
    if (days.isBlank()) return false
    val value = days.trim().toLongOrNull()
    return value == null || value !in 1..MAX_EXPIRY_DAYS
}

private const val TRANSITION_KEY = "deleteObjectsTransition"

private val EDITED_KEYS = setOf("id", "enabled", "conditions", TRANSITION_KEY)
