package com.tomasps.virga.core.network

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.CloudflareMessage
import com.tomasps.virga.core.common.result.PageInfo
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

private const val UNIT_SERIAL_NAME = "kotlin.Unit"
private const val NOT_AN_ENVELOPE = "Response body is not a JSON envelope"
private const val UNEXPECTED_SHAPE = "Response result has an unexpected shape"

/**
 * Decodes the `{success, errors, messages, result, result_info}` envelope. Non-2xx statuses map to typed
 * errors first, so a non-JSON error page (for example a gateway HTML page) still maps by status.
 */
internal fun <T> decodeEnvelope(
    json: Json,
    status: Int,
    body: String,
    retryAfterMillis: Long?,
    deserializer: DeserializationStrategy<T>,
): ApiResult<T> {
    val root = parseJsonObject(json, body)
    if (!isSuccessStatus(status)) return errorResult(status, root, retryAfterMillis)
    // A 204 or an empty 2xx answers a call that returns nothing. Only Unit-typed calls may accept it.
    if (body.isBlank() && isUnitDeserializer(deserializer)) return ApiResult.Success(unitValue())
    if (root == null) return ApiResult.Failure(ApiError.Decode(NOT_AN_ENVELOPE))
    if ((root["success"] as? JsonPrimitive)?.booleanOrNull == false) {
        return ApiResult.Failure(ApiError.Rejected(status, envelopeMessages(root)))
    }
    return when (val decoded = decodeResult(json, root["result"], deserializer)) {
        is ApiResult.Success -> ApiResult.Success(decoded.value, parsePageInfo(root["result_info"]))
        is ApiResult.Failure -> decoded
    }
}

/** Maps a failed response to [ApiResult.Failure] using whatever envelope messages the body carries. */
internal fun errorResult(status: Int, root: JsonObject?, retryAfterMillis: Long?): ApiResult<Nothing> {
    val messages = root?.let(::envelopeMessages).orEmpty()
    return ApiResult.Failure(errorForStatus(status, messages, retryAfterMillis))
}

internal fun <T> decodeResult(json: Json, element: JsonElement?, deserializer: DeserializationStrategy<T>): ApiResult<T> {
    if (isUnitDeserializer(deserializer)) return ApiResult.Success(unitValue())
    return try {
        ApiResult.Success(json.decodeFromJsonElement(deserializer, element ?: JsonNull))
    } catch (error: SerializationException) {
        ApiResult.Failure(ApiError.Decode(UNEXPECTED_SHAPE))
    } catch (error: IllegalArgumentException) {
        ApiResult.Failure(ApiError.Decode(UNEXPECTED_SHAPE))
    }
}

private fun isUnitDeserializer(deserializer: DeserializationStrategy<*>): Boolean =
    deserializer.descriptor.serialName == UNIT_SERIAL_NAME

@Suppress("UNCHECKED_CAST")
private fun <T> unitValue(): T = Unit as T

internal fun parseJsonObject(json: Json, body: String): JsonObject? = try {
    json.parseToJsonElement(body) as? JsonObject
} catch (error: SerializationException) {
    null
} catch (error: IllegalArgumentException) {
    null
}

internal fun envelopeMessages(root: JsonObject): List<CloudflareMessage> =
    parseMessages(root["errors"]) + parseMessages(root["messages"])

internal fun parseMessages(element: JsonElement?): List<CloudflareMessage> =
    (element as? JsonArray)?.mapNotNull(::parseMessage).orEmpty()

private fun parseMessage(element: JsonElement): CloudflareMessage? = when (element) {
    is JsonObject -> CloudflareMessage(code = element.int("code"), message = element.string("message"))
    is JsonPrimitive -> if (element.isString) CloudflareMessage(message = element.content) else null
    else -> null
}

/** result_info in page style (page, total_count) or cursor style (cursors.after). Null when absent. */
internal fun parsePageInfo(element: JsonElement?): PageInfo? {
    val info = element as? JsonObject ?: return null
    val cursors = info["cursors"] as? JsonObject
    if (cursors != null || info.containsKey("cursor")) {
        val next = (cursors?.get("after") ?: info["cursor"])?.let(::stringOf)?.takeIf { it.isNotBlank() }
        return PageInfo.Cursor(nextCursor = next, perPage = info.int("per_page"), count = info.int("count"))
    }
    val isPageStyle = info.containsKey("page") || info.containsKey("total_count") || info.containsKey("per_page")
    if (!isPageStyle) return null
    return PageInfo.Page(
        page = info.int("page"),
        perPage = info.int("per_page"),
        count = info.int("count"),
        totalCount = info.int("total_count"),
    )
}

private fun stringOf(element: JsonElement): String? =
    (element as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

internal fun JsonObject.string(key: String): String? = this[key]?.let(::stringOf)

internal fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
