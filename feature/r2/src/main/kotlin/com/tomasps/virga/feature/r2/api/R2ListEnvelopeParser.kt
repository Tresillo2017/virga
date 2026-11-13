package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

private const val NOT_AN_ENVELOPE = "R2 list response is not a JSON envelope"
private const val FAILED_ENVELOPE = "R2 list response reported success=false"

/**
 * Reads R2 list responses from the raw body. The typed helpers in core:network drop `result_info`, which holds
 * the folder prefixes (`delimited`) and the truncation flag, so listing reads the whole envelope. Parsing is
 * defensive: a missing field gives a default, and an item without a key is skipped rather than failing a page.
 */
class R2ListEnvelopeParser(private val json: Json) {

    fun parseObjectPage(body: String): ApiResult<R2ObjectPage> {
        val root = envelopeOrNull(body) ?: return decodeFailure(NOT_AN_ENVELOPE)
        if (root.isFailed()) return decodeFailure(FAILED_ENVELOPE)
        val items = (root["result"] as? JsonArray).orEmpty().mapNotNull { it.asObjectOrNull()?.toObjectItem() }
        val info = root["result_info"] as? JsonObject
        val cursor = info?.stringOrNull("cursor")
        val hasMore = info?.get("is_truncated")?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: !cursor.isNullOrEmpty()
        val folders = (info?.get("delimited") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return ApiResult.Success(
            R2ObjectPage(
                objects = items,
                folders = folders,
                nextCursor = cursor.takeIf { hasMore && !it.isNullOrEmpty() },
            ),
        )
    }

    fun parseBucketPage(body: String): ApiResult<R2BucketPage> {
        val root = envelopeOrNull(body) ?: return decodeFailure(NOT_AN_ENVELOPE)
        if (root.isFailed()) return decodeFailure(FAILED_ENVELOPE)
        val buckets = ((root["result"] as? JsonObject)?.get("buckets") as? JsonArray).orEmpty()
            .mapNotNull { element -> runCatching { json.decodeFromJsonElement(R2BucketInfo.serializer(), element) }.getOrNull() }
            .filter { it.name.isNotBlank() }
        val cursor = (root["result_info"] as? JsonObject)?.stringOrNull("cursor")
        return ApiResult.Success(R2BucketPage(buckets = buckets, nextCursor = cursor?.takeIf { it.isNotEmpty() }))
    }

    private fun envelopeOrNull(body: String): JsonObject? = try {
        json.parseToJsonElement(body) as? JsonObject
    } catch (error: IllegalArgumentException) {
        null
    } catch (error: kotlinx.serialization.SerializationException) {
        null
    }

    private fun JsonObject.isFailed(): Boolean = (this["success"] as? JsonPrimitive)?.booleanOrNull == false

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonObject.toObjectItem(): R2ObjectItem? {
        val key = stringOrNull("key")?.takeIf { it.isNotEmpty() } ?: return null
        val httpMetadata = this["http_metadata"] as? JsonObject
        return R2ObjectItem(
            key = key,
            size = (this["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
            etag = stringOrNull("etag"),
            lastModified = stringOrNull("last_modified"),
            contentType = httpMetadata?.stringOrNull("contentType"),
            customMetadata = (this["custom_metadata"] as? JsonObject).orEmpty()
                .mapNotNull { (name, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { name to it } }
                .toMap(),
            storageClass = stringOrNull("storage_class"),
        )
    }

    private fun decodeFailure(reason: String): ApiResult<Nothing> = ApiResult.Failure(ApiError.Decode(reason))
}

private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList().orEmpty()

private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this?.toMap().orEmpty()
