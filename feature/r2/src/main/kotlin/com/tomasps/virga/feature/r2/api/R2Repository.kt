package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.CloudflareJson
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

private const val PAGE_SIZE = 50
private const val DEFAULT_OBJECT_DELIMITER = "/"
private const val OCTET_STREAM = "application/octet-stream"
private const val UNSAFE_KEY_REASON = "object key has an empty or dot segment"


/** Object-level and bucket-level R2 calls over [CloudflareApi]. Errors come back as [ApiResult]; no UI text here. */
class R2Repository(
    private val api: CloudflareApi,
    private val parser: R2ListEnvelopeParser = R2ListEnvelopeParser(CloudflareJson),
) {

    // ---- Buckets -----------------------------------------------------------------------------------------------

    suspend fun listBuckets(accountId: String, cursor: String?, nameContains: String?): ApiResult<R2BucketPage> {
        val query = buildMap<String, String?> {
            put("per_page", PAGE_SIZE.toString())
            cursor?.let { put("cursor", it) }
            nameContains?.takeIf { it.isNotBlank() }?.let { put("name_contains", it) }
        }
        return api.getText(bucketsPath(accountId), query).andThen { parser.parseBucketPage(it) }
    }

    suspend fun bucket(accountId: String, bucketName: String): ApiResult<R2BucketInfo> =
        api.get(bucketPath(accountId, bucketName))

    suspend fun createBucket(accountId: String, name: String, location: String?, storageClass: String?): ApiResult<Unit> {
        val body = buildJsonObject {
            put("name", name)
            location?.let { put("locationHint", it) }
            storageClass?.let { put("storageClass", it) }
        }
        return api.post<JsonObject, Unit>(bucketsPath(accountId), body)
    }

    suspend fun deleteBucket(accountId: String, bucketName: String): ApiResult<Unit> =
        api.delete(bucketPath(accountId, bucketName))

    // ---- Objects -----------------------------------------------------------------------------------------------

    /**
     * One page of objects. With [delimiter] set, the response groups keys into [R2ObjectPage.folders]. A search
     * passes no delimiter, so every key that starts with [prefix] comes back flat.
     */
    suspend fun listObjects(
        accountId: String,
        bucketName: String,
        prefix: String,
        delimiter: Boolean,
        cursor: String?,
    ): ApiResult<R2ObjectPage> {
        val query: Map<String, String?> = buildMap {
            put("per_page", PAGE_SIZE.toString())
            if (prefix.isNotEmpty()) put("prefix", prefix)
            if (delimiter) put("delimiter", DEFAULT_OBJECT_DELIMITER)
            cursor?.let { put("cursor", it) }
        }
        return api.getText(objectsPath(accountId, bucketName), query).andThen { parser.parseObjectPage(it) }
    }

    /**
     * Whether an object with exactly [objectKey] exists. Keys sort before any longer key that starts with them, so
     * the first page of a prefix listing holds the exact key if it is there.
     */
    suspend fun objectExists(accountId: String, bucketName: String, objectKey: String): ApiResult<Boolean> =
        listObjects(accountId, bucketName, prefix = objectKey, delimiter = false, cursor = null)
            .map { page -> page.objects.any { it.key == objectKey } }

    suspend fun deleteObject(accountId: String, bucketName: String, objectKey: String): ApiResult<Unit> =
        guardKey(objectKey) { api.delete(objectPath(accountId, bucketName, objectKey)) }

    /** Streams [write]'s bytes as the body of one PUT. [contentLength] must be exact. */
    suspend fun uploadObject(
        accountId: String,
        bucketName: String,
        objectKey: String,
        contentType: String?,
        contentLength: Long,
        write: suspend (ByteWriteChannel) -> Unit,
    ): ApiResult<Unit> = guardKey(objectKey) {
        api.uploadStream(
            method = HttpMethod.Put,
            path = objectPath(accountId, bucketName, objectKey),
            contentType = parseContentType(contentType),
            contentLength = contentLength,
            write = write,
            deserializer = serializer<Unit>(),
        )
    }

    /** Streams the object body to [consume]. Errors return before [consume] runs. */
    suspend fun <R> downloadObject(
        accountId: String,
        bucketName: String,
        objectKey: String,
        consume: suspend (ByteReadChannel, Long?) -> R,
    ): ApiResult<R> = guardKey(objectKey) {
        api.download(objectPath(accountId, bucketName, objectKey), consume = consume)
    }

    // ---- Public access and domains -------------------------------------------------------------------------------

    suspend fun managedDomain(accountId: String, bucketName: String): ApiResult<R2ManagedDomain> =
        api.get(managedDomainPath(accountId, bucketName))

    suspend fun setManagedDomainEnabled(accountId: String, bucketName: String, enabled: Boolean): ApiResult<Unit> =
        api.put<JsonObject, Unit>(managedDomainPath(accountId, bucketName), buildJsonObject { put("enabled", enabled) })

    suspend fun customDomains(accountId: String, bucketName: String): ApiResult<List<R2CustomDomain>> =
        api.get<R2CustomDomainList>(customDomainsPath(accountId, bucketName)).map { it.domains }

    suspend fun addCustomDomain(accountId: String, bucketName: String, domain: String, zoneId: String, minTls: String?): ApiResult<Unit> {
        val body = buildJsonObject {
            put("domain", domain)
            put("zoneId", zoneId)
            put("enabled", true)
            minTls?.let { put("minTLS", it) }
        }
        return api.post<JsonObject, Unit>(customDomainsPath(accountId, bucketName), body)
    }

    suspend fun removeCustomDomain(accountId: String, bucketName: String, domain: String): ApiResult<Unit> =
        api.delete(customDomainPath(accountId, bucketName, domain))

    // ---- CORS, lock, lifecycle ----------------------------------------------------------------------------------

    suspend fun corsRules(accountId: String, bucketName: String): ApiResult<List<R2CorsRule>> =
        api.get<R2CorsConfig>(corsPath(accountId, bucketName)).map { it.rules }

    suspend fun replaceCorsRules(accountId: String, bucketName: String, rules: List<R2CorsRule>): ApiResult<Unit> =
        api.put<JsonObject, Unit>(corsPath(accountId, bucketName), corsBody(rules))

    suspend fun deleteCorsRules(accountId: String, bucketName: String): ApiResult<Unit> =
        api.delete(corsPath(accountId, bucketName))

    suspend fun lockRules(accountId: String, bucketName: String): ApiResult<List<R2LockRule>> =
        api.get<R2LockConfig>(lockPath(accountId, bucketName)).map { it.rules }

    suspend fun replaceLockRules(accountId: String, bucketName: String, rules: List<R2LockRule>): ApiResult<Unit> =
        api.put<JsonObject, Unit>(lockPath(accountId, bucketName), lockBody(rules))

    /** Lifecycle rules as raw JSON, so fields Virga does not model are written back unchanged. */
    suspend fun lifecycleRules(accountId: String, bucketName: String): ApiResult<List<JsonObject>> =
        api.get<JsonObject>(lifecyclePath(accountId, bucketName)).map { root ->
            (root["rules"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        }

    suspend fun replaceLifecycleRules(accountId: String, bucketName: String, rules: List<JsonObject>): ApiResult<Unit> =
        api.put<JsonObject, Unit>(lifecyclePath(accountId, bucketName), buildJsonObject {
            put("rules", JsonArray(rules))
        })

    private fun corsBody(rules: List<R2CorsRule>): JsonObject = buildJsonObject {
        put("rules", buildJsonArray {
            rules.forEach { rule ->
                add(buildJsonObject {
                    rule.id?.takeIf { it.isNotBlank() }?.let { put("id", it) }
                    put("allowed", buildJsonObject {
                        put("origins", stringArray(rule.allowed.origins))
                        put("methods", stringArray(rule.allowed.methods))
                        if (rule.allowed.headers.isNotEmpty()) put("headers", stringArray(rule.allowed.headers))
                    })
                    if (rule.exposeHeaders.isNotEmpty()) put("exposeHeaders", stringArray(rule.exposeHeaders))
                    rule.maxAgeSeconds?.let { put("maxAgeSeconds", it) }
                })
            }
        })
    }

    private fun lockBody(rules: List<R2LockRule>): JsonObject = buildJsonObject {
        put("rules", buildJsonArray {
            rules.forEach { rule ->
                add(buildJsonObject {
                    put("id", rule.id)
                    put("enabled", rule.enabled)
                    rule.prefix?.takeIf { it.isNotEmpty() }?.let { put("prefix", it) }
                    put("condition", lockCondition(rule.condition))
                })
            }
        })
    }

    private fun lockCondition(condition: R2LockCondition): JsonObject = buildJsonObject {
        put("type", condition.type)
        condition.maxAgeSeconds?.let { put("maxAgeSeconds", it) }
        condition.date?.let { put("date", it) }
    }

    private fun stringArray(values: List<String>): JsonArray = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

    private suspend fun <T> guardKey(objectKey: String, call: suspend () -> ApiResult<T>): ApiResult<T> =
        if (isSafeObjectKey(objectKey)) call() else ApiResult.Failure(ApiError.Local(UNSAFE_KEY_REASON))

    private fun parseContentType(value: String?): ContentType =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { ContentType.parse(it) }.getOrNull() }
            ?: ContentType.parse(OCTET_STREAM)
}

/** Chains a second step that only runs when the first call succeeded. The failure passes through unchanged. */
internal inline fun <T, R> ApiResult<T>.andThen(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(value)
    is ApiResult.Failure -> this
}
