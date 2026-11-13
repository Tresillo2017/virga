package com.tomasps.virga.feature.kv.value

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

const val KV_MAX_KEY_BYTES = 512
const val KV_MAX_VALUE_BYTES = 25L * 1024 * 1024
const val KV_MAX_METADATA_BYTES = 1024
const val KV_MAX_TITLE_CHARS = 512

enum class KeyNameProblem { EMPTY, DOT_SEGMENT, TOO_LONG }

enum class ValueProblem { TOO_LARGE }

enum class MetadataProblem { INVALID_JSON, TOO_LARGE }

/** Result of checking the metadata editor's text. [element] is null when no metadata should be sent. */
sealed interface MetadataCheck {
    data class Valid(val element: JsonElement?) : MetadataCheck

    data class Invalid(val problem: MetadataProblem) : MetadataCheck
}

private const val DOT = "."
private const val DOT_DOT = ".."

/** Checks a key name before any request. The reference limits names to 512 bytes of UTF-8 and refuses "." and "..". */
fun validateKeyName(name: String): KeyNameProblem? = when {
    name.isEmpty() -> KeyNameProblem.EMPTY
    name == DOT || name == DOT_DOT -> KeyNameProblem.DOT_SEGMENT
    name.encodeToByteArray().size > KV_MAX_KEY_BYTES -> KeyNameProblem.TOO_LONG
    else -> null
}

fun validateValueSize(sizeBytes: Long): ValueProblem? = ValueProblem.TOO_LARGE.takeIf { sizeBytes > KV_MAX_VALUE_BYTES }

/**
 * Parses the metadata editor's text. Blank text and a JSON null both mean "no metadata". The size limit is measured
 * on the compact JSON that will be sent, not on what the user typed.
 */
fun validateMetadataText(text: String): MetadataCheck {
    if (text.isBlank()) return MetadataCheck.Valid(null)
    val element = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        ?: return MetadataCheck.Invalid(MetadataProblem.INVALID_JSON)
    if (element == JsonNull) return MetadataCheck.Valid(null)
    val compact = element.toString().encodeToByteArray().size
    return if (compact > KV_MAX_METADATA_BYTES) {
        MetadataCheck.Invalid(MetadataProblem.TOO_LARGE)
    } else {
        MetadataCheck.Valid(element)
    }
}
