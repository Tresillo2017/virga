package com.tomasps.virga.feature.kv.value

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

const val KV_BULK_MAX_PAIRS = 10_000
const val KV_BULK_MAX_BYTES = 100L * 1024 * 1024

/** Why a whole import file was refused. */
enum class BulkImportProblem { NOT_JSON, NOT_AN_ARRAY, TOO_MANY_PAIRS }

/** Why one pair was left out before anything was sent. */
enum class PairProblem {
    NOT_AN_OBJECT,
    MISSING_KEY,
    MISSING_VALUE,
    KEY_EMPTY,
    KEY_DOT_SEGMENT,
    KEY_TOO_LONG,
    VALUE_TOO_LARGE,
    TTL_TOO_SHORT,
}

/** One pair that passed the local checks. [element] is the object exactly as it will be sent. [bytes] sizes the chunks. */
data class ParsedPair(val key: String, val element: JsonElement, val bytes: Int)

data class RejectedPair(val key: String, val problem: PairProblem)

data class BulkImportParse(
    val pairs: List<ParsedPair>,
    val rejected: List<RejectedPair>,
    val problem: BulkImportProblem?,
)

/**
 * Parses a bulk-write file: a JSON array of objects with `key` and `value`, plus the optional `base64`, `expiration`,
 * `expiration_ttl` and `metadata`. Pairs that fail a local check are reported and never sent. A file that is not
 * an array, or holds more than 10,000 pairs, is refused as a whole.
 */
fun parseBulkImport(text: String): BulkImportParse {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        ?: return refused(BulkImportProblem.NOT_JSON)
    val items = (root as? JsonArray) ?: return refused(BulkImportProblem.NOT_AN_ARRAY)
    if (items.size > KV_BULK_MAX_PAIRS) return refused(BulkImportProblem.TOO_MANY_PAIRS)
    val pairs = mutableListOf<ParsedPair>()
    val rejected = mutableListOf<RejectedPair>()
    items.forEach { item ->
        when (val check = checkPair(item)) {
            is PairCheck.Ok -> pairs += check.pair
            is PairCheck.Rejected -> rejected += RejectedPair(check.key, check.problem)
        }
    }
    return BulkImportParse(pairs = pairs, rejected = rejected, problem = null)
}

/** The two brackets of the JSON array every chunk is sent as. */
private const val ARRAY_OVERHEAD_BYTES = 2L

/** One comma per pair. Reserved for every pair, which is one more than the commas a chunk really has. */
private const val SEPARATOR_BYTES = 1L

/**
 * Splits pairs into chunks that respect both the pair count and the byte budget. The budget is the size of the JSON
 * array that is sent, so the brackets and the commas between pairs count too. Order is kept.
 */
fun chunkBulkPairs(
    pairs: List<ParsedPair>,
    maxPairs: Int = KV_BULK_MAX_PAIRS,
    maxBytes: Long = KV_BULK_MAX_BYTES,
): List<List<ParsedPair>> {
    val chunks = mutableListOf<List<ParsedPair>>()
    var current = mutableListOf<ParsedPair>()
    var currentBytes = ARRAY_OVERHEAD_BYTES
    for (pair in pairs) {
        val cost = pair.bytes + SEPARATOR_BYTES
        val wouldOverflow = current.size >= maxPairs || currentBytes + cost > maxBytes
        if (wouldOverflow && current.isNotEmpty()) {
            chunks += current
            current = mutableListOf()
            currentBytes = ARRAY_OVERHEAD_BYTES
        }
        current += pair
        currentBytes += cost
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
}

private sealed interface PairCheck {
    data class Ok(val pair: ParsedPair) : PairCheck

    data class Rejected(val key: String, val problem: PairProblem) : PairCheck
}

private fun refused(problem: BulkImportProblem) = BulkImportParse(emptyList(), emptyList(), problem)

private fun checkPair(item: JsonElement): PairCheck {
    val obj = item as? JsonObject ?: return PairCheck.Rejected("", PairProblem.NOT_AN_OBJECT)
    val key = (obj["key"] as? JsonPrimitive)?.contentOrNull
        ?: return PairCheck.Rejected("", PairProblem.MISSING_KEY)
    keyProblem(key)?.let { return PairCheck.Rejected(key, it) }
    val text = (obj["value"] as? JsonPrimitive)?.contentOrNull
        ?: return PairCheck.Rejected(key, PairProblem.MISSING_VALUE)
    if (text.encodeToByteArray().size > KV_MAX_VALUE_BYTES) return PairCheck.Rejected(key, PairProblem.VALUE_TOO_LARGE)
    val ttl = (obj["expiration_ttl"] as? JsonPrimitive)?.longOrNull
    if (ttl != null && ttl < KV_MIN_EXPIRATION_SECONDS) return PairCheck.Rejected(key, PairProblem.TTL_TOO_SHORT)
    // The request limit counts the whole JSON object (escaping and metadata included), so size that, not just the text.
    return PairCheck.Ok(ParsedPair(key = key, element = obj, bytes = obj.toString().encodeToByteArray().size))
}

private fun keyProblem(key: String): PairProblem? = when (validateKeyName(key)) {
    null -> null
    KeyNameProblem.EMPTY -> PairProblem.KEY_EMPTY
    KeyNameProblem.DOT_SEGMENT -> PairProblem.KEY_DOT_SEGMENT
    KeyNameProblem.TOO_LONG -> PairProblem.KEY_TOO_LONG
}
