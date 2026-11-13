package com.tomasps.virga.feature.kv.demo

import com.tomasps.virga.feature.kv.data.KV_MULTIPART_BOUNDARY
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** A single-value write as the demo reads it: the value bytes, and the metadata when the part was sent. */
internal class KvDemoWrite(val value: ByteArray, val metadata: JsonElement?)

private const val CRLF = "\r\n"
private val BOUNDARY_LINE = "--$KV_MULTIPART_BOUNDARY".encodeToByteArray()
private val BLANK_LINE = "$CRLF$CRLF".encodeToByteArray()
private val BODY_BREAK = "$CRLF--$KV_MULTIPART_BOUNDARY".encodeToByteArray()
private val METADATA_NAME = "name=\"metadata\"".encodeToByteArray()
private const val NOT_FOUND = -1

/**
 * Reads a PUT body. The client sends multipart with the fixed boundary; anything else is taken as the raw value.
 * Demo requests carry no headers, so the boundary is what tells the two apart.
 */
internal fun parseKvWriteBody(body: ByteArray): KvDemoWrite {
    if (!body.hasBytePrefix(BOUNDARY_LINE)) return KvDemoWrite(body, null)
    val valueStart = indexOf(body, BLANK_LINE, 0).let { if (it == NOT_FOUND) body.size else it + BLANK_LINE.size }
    val valueEnd = indexOf(body, BODY_BREAK, valueStart).let { if (it == NOT_FOUND) body.size else it }
    val value = body.copyOfRange(valueStart, valueEnd)
    return KvDemoWrite(value = value, metadata = metadataPart(body, valueEnd))
}

/** The metadata part after the value, parsed as JSON. Null when there is none or it is not JSON. */
private fun metadataPart(body: ByteArray, afterValue: Int): JsonElement? {
    val nameAt = indexOf(body, METADATA_NAME, afterValue)
    if (nameAt == NOT_FOUND) return null
    val start = indexOf(body, BLANK_LINE, nameAt).let { if (it == NOT_FOUND) return null else it + BLANK_LINE.size }
    val end = indexOf(body, BODY_BREAK, start).let { if (it == NOT_FOUND) body.size else it }
    val text = body.copyOfRange(start, end).decodeToString()
    return runCatching { Json.parseToJsonElement(text) }.getOrNull()?.takeUnless { it == JsonNull }
}

/** One pair of a bulk write, as the demo stores it. */
internal class KvDemoBulkPair(val key: String, val value: ByteArray, val metadata: JsonElement?, val expiresAt: Long?)

/**
 * Reads a bulk-write array. Items without a key or a value are skipped, because the app never sends them; the demo
 * only needs to answer for what it stored.
 */
internal fun parseKvBulkPairs(text: String, nowEpochSeconds: Long): List<KvDemoBulkPair> {
    val items = (Json.parseToJsonElement(text) as? JsonArray).orEmpty()
    return items.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val key = (obj["key"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val value = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val isBase64 = (obj["base64"] as? JsonPrimitive)?.contentOrNull == "true"
        val bytes = if (isBase64) decodeBase64(value) else value.encodeToByteArray()
        KvDemoBulkPair(
            key = key,
            value = bytes,
            metadata = obj["metadata"]?.takeUnless { it == JsonNull },
            expiresAt = expiresAtOf(obj, nowEpochSeconds),
        )
    }
}

/** The bulk delete body: an array of key names. */
internal fun parseKvDeleteNames(text: String): List<String> =
    (Json.parseToJsonElement(text) as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

/** Absolute expiration from a query value or a TTL, as the API takes them. Null when neither is set. */
internal fun expiresAtOf(query: Map<String, String>, nowEpochSeconds: Long): Long? {
    query["expiration_ttl"]?.toLongOrNull()?.let { return nowEpochSeconds + it }
    return query["expiration"]?.toLongOrNull()
}

/** Expiration from one bulk item: an absolute `expiration`, or an `expiration_ttl` from now. */
internal fun expiresAtOf(item: JsonObject, nowEpochSeconds: Long): Long? {
    item["expiration_ttl"]?.let { ttl -> (ttl as? JsonPrimitive)?.longOrNull?.let { return nowEpochSeconds + it } }
    return (item["expiration"] as? JsonPrimitive)?.longOrNull
}

private fun decodeBase64(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)

private fun ByteArray.hasBytePrefix(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

/** First index of [needle] in [haystack] at or after [from], or [NOT_FOUND]. Byte-level, so binary values are safe. */
private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
    if (needle.isEmpty() || haystack.size < needle.size) return NOT_FOUND
    for (start in from..haystack.size - needle.size) {
        if (needle.indices.all { haystack[start + it] == needle[it] }) return start
    }
    return NOT_FOUND
}
