package com.tomasps.virga.feature.kv.data

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.PageInfo
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.allPages
import com.tomasps.virga.core.network.cloudflarePath
import com.tomasps.virga.feature.kv.value.KvExpiration
import io.ktor.http.HttpMethod
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import kotlinx.io.readByteArray
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

private const val KEY_PAGE_MIN = 10
private const val KEY_PAGE_MAX = 1000
private const val NAMESPACE_PAGE_SIZE = 1000
private const val LOOKUP_MAX_PAGES = 5

/** One page of keys. [nextCursor] is null on the last page. */
data class KvKeyPage(
    val keys: List<KvKeyInfo>,
    val nextCursor: String?,
)

/**
 * Namespace, key, value and bulk calls under /accounts/{id}/storage/kv. Every path is built with cloudflarePath, so
 * key names with slashes and other characters stay one segment. The legacy /workers/namespaces routes are not used.
 */
class KvRepository(private val api: CloudflareApi) {

    suspend fun namespaces(accountId: String): ApiResult<List<KvNamespaceDto>> = api.allPages(
        path = namespacesPath(accountId),
        deserializer = ListSerializer(KvNamespaceDto.serializer()),
        perPage = NAMESPACE_PAGE_SIZE,
    )

    suspend fun namespace(accountId: String, namespaceId: String): ApiResult<KvNamespaceDto> =
        api.get(namespacePath(accountId, namespaceId))

    suspend fun createNamespace(
        accountId: String,
        title: String,
        jurisdiction: KvJurisdiction? = null,
    ): ApiResult<KvNamespaceDto> = api.post(
        namespacesPath(accountId),
        KvNamespaceTitleBody(title = title, jurisdiction = jurisdiction?.wire),
    )

    suspend fun renameNamespace(accountId: String, namespaceId: String, title: String): ApiResult<Unit> =
        api.put<KvNamespaceTitleBody, Unit>(namespacePath(accountId, namespaceId), KvNamespaceTitleBody(title))

    suspend fun deleteNamespace(accountId: String, namespaceId: String): ApiResult<Unit> =
        api.delete<Unit>(namespacePath(accountId, namespaceId))

    /** One page of keys. [limit] is clamped to the 10 to 1000 range the endpoint accepts. */
    suspend fun keyPage(
        accountId: String,
        namespaceId: String,
        prefix: String?,
        cursor: String?,
        limit: Int,
    ): ApiResult<KvKeyPage> {
        val query = buildMap<String, String> {
            prefix?.takeIf { it.isNotEmpty() }?.let { put("prefix", it) }
            cursor?.let { put("cursor", it) }
            put("limit", limit.coerceIn(KEY_PAGE_MIN, KEY_PAGE_MAX).toString())
        }
        val result = api.get<List<KvKeyDto>>(cloudflarePath(*keysSegments(accountId, namespaceId)), query)
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(
                value = KvKeyPage(
                    keys = result.value.map { KvKeyInfo(it.name, it.expiration, it.metadata) },
                    nextCursor = (result.pageInfo as? PageInfo.Cursor)?.nextCursor,
                ),
            )
        }
    }

    /**
     * Finds one key by its exact name, scanning the prefix search. Success(null) means the scan covered every match
     * and the key is absent. When the scan hits [LOOKUP_MAX_PAGES] with more matches left, the answer is unknown and a
     * Local failure is returned, so a caller never takes "not scanned" for "absent".
     */
    suspend fun findKey(accountId: String, namespaceId: String, name: String): ApiResult<KvKeyInfo?> {
        var cursor: String? = null
        repeat(LOOKUP_MAX_PAGES) {
            val page = when (val result = keyPage(accountId, namespaceId, prefix = name, cursor = cursor, limit = KEY_PAGE_MAX)) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> result.value
            }
            page.keys.firstOrNull { it.name == name }?.let { return ApiResult.Success(it) }
            cursor = page.nextCursor ?: return ApiResult.Success(null)
        }
        return ApiResult.Failure(ApiError.Local(reason = "prefix lookup stopped after $LOOKUP_MAX_PAGES pages"))
    }

    /**
     * Metadata of one key, or null when the key has none or does not exist. A JSON null is also treated as no metadata.
     */
    suspend fun metadata(accountId: String, namespaceId: String, key: String): ApiResult<JsonElement?> {
        val result = api.get<JsonElement?>(metadataPath(accountId, namespaceId, key))
        return when {
            result is ApiResult.Failure && result.error is ApiError.NotFound -> ApiResult.Success(null)
            result is ApiResult.Success -> ApiResult.Success(result.value.takeUnless { it == JsonNull })
            else -> result
        }
    }

    /**
     * Reads a value as bytes. Returns null, without reading the body, when the declared length is above [maxBytes].
     */
    suspend fun readValue(
        accountId: String,
        namespaceId: String,
        key: String,
        maxBytes: Long,
    ): ApiResult<ByteArray?> = api.download(valuePath(accountId, namespaceId, key)) { channel, length ->
        if (length != null && length > maxBytes) null else readAtMost(channel, maxBytes)
    }

    /** Writes one value with optional metadata and expiration. The expiration goes in the query string. */
    suspend fun writeValue(
        accountId: String,
        namespaceId: String,
        key: String,
        value: ByteArray,
        metadata: JsonElement?,
        expiration: KvExpiration,
    ): ApiResult<Unit> {
        val body = KvMultipartBody.of(value, metadata)
        return api.uploadStream(
            method = HttpMethod.Put,
            path = valuePath(accountId, namespaceId, key),
            contentType = KvMultipartBody.contentType,
            contentLength = body.length,
            write = { channel ->
                channel.writeFully(body.prefix)
                channel.writeFully(body.value)
                channel.writeFully(body.suffix)
            },
            deserializer = serializer<Unit>(),
            query = expiration.toQuery(),
        )
    }

    suspend fun deleteValue(accountId: String, namespaceId: String, key: String): ApiResult<Unit> =
        api.delete<Unit>(valuePath(accountId, namespaceId, key))

    /** Bulk write. [pairs] is the JSON array the bulk-write format defines. */
    suspend fun bulkWrite(accountId: String, namespaceId: String, pairs: JsonArray): ApiResult<KvBulkOutcome> =
        api.put<JsonArray, KvBulkResult?>(
            cloudflarePath(*bulkSegments(accountId, namespaceId)),
            pairs,
        ).map { it.toOutcome() }

    /** Bulk delete of up to 10,000 keys per call. */
    suspend fun bulkDelete(accountId: String, namespaceId: String, keys: List<String>): ApiResult<KvBulkOutcome> =
        api.post<JsonArray, KvBulkResult?>(
            cloudflarePath(*bulkDeleteSegments(accountId, namespaceId)),
            JsonArray(keys.map { JsonPrimitive(it) }),
        ).map { it.toOutcome() }

    private fun namespacesPath(accountId: String): String =
        cloudflarePath("accounts", accountId, "storage", "kv", "namespaces")

    private fun namespacePath(accountId: String, namespaceId: String): String =
        cloudflarePath("accounts", accountId, "storage", "kv", "namespaces", namespaceId)

    private fun valuePath(accountId: String, namespaceId: String, key: String): String =
        cloudflarePath("accounts", accountId, "storage", "kv", "namespaces", namespaceId, "values", key)

    private fun metadataPath(accountId: String, namespaceId: String, key: String): String =
        cloudflarePath("accounts", accountId, "storage", "kv", "namespaces", namespaceId, "metadata", key)

    private fun keysSegments(accountId: String, namespaceId: String): Array<String> =
        arrayOf("accounts", accountId, "storage", "kv", "namespaces", namespaceId, "keys")

    private fun bulkSegments(accountId: String, namespaceId: String): Array<String> =
        arrayOf("accounts", accountId, "storage", "kv", "namespaces", namespaceId, "bulk")

    private fun bulkDeleteSegments(accountId: String, namespaceId: String): Array<String> =
        arrayOf("accounts", accountId, "storage", "kv", "namespaces", namespaceId, "bulk", "delete")
}

private fun KvBulkResult?.toOutcome(): KvBulkOutcome = KvBulkOutcome(
    successfulCount = this?.successfulKeyCount ?: 0,
    unsuccessfulKeys = this?.unsuccessfulKeys.orEmpty(),
)

/** Reads the whole body if it fits in [maxBytes]; otherwise returns null so the caller can refuse it. */
private suspend fun readAtMost(channel: ByteReadChannel, maxBytes: Long): ByteArray? {
    val bytes = channel.readRemaining(maxBytes + 1).readByteArray()
    return bytes.takeIf { it.size <= maxBytes }
}
