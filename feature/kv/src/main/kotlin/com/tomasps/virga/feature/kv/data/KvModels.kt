package com.tomasps.virga.feature.kv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** One namespace as the storage/kv/namespaces routes return it. Unknown fields are ignored. */
@Serializable
data class KvNamespaceDto(
    val id: String,
    val title: String = "",
    val jurisdiction: String? = null,
    val mode: String? = null,
    @SerialName("supports_url_encoding") val supportsUrlEncoding: Boolean? = null,
)

/** One key from the key list. [expiration] is absolute epoch seconds even when it was written as a TTL. */
@Serializable
data class KvKeyDto(
    val name: String,
    val expiration: Long? = null,
    val metadata: JsonElement? = null,
)

/** Where a namespace's data is kept. Set at creation only; the API has no way to change it afterwards. */
enum class KvJurisdiction(val wire: String) { EU("eu"), FEDRAMP("fedramp"), US("us") }

/**
 * Body for create and rename. Title is required. Jurisdiction is only sent at creation, and a null one is left out of
 * the JSON (the app's JSON config omits nulls), which gives the default location.
 */
@Serializable
data class KvNamespaceTitleBody(val title: String, val jurisdiction: String? = null)

/**
 * Result of a bulk write or delete. Both fields are optional in the reference, so a missing result still decodes.
 */
@Serializable
data class KvBulkResult(
    @SerialName("successful_key_count") val successfulKeyCount: Int? = null,
    @SerialName("unsuccessful_keys") val unsuccessfulKeys: List<String> = emptyList(),
)

/** A key row plus its expiration and metadata, as the list reports them. */
data class KvKeyInfo(
    val name: String,
    val expirationEpochSeconds: Long?,
    val metadata: JsonElement?,
)

/** The outcome of a bulk call: how many keys landed, and which ones did not. */
data class KvBulkOutcome(
    val successfulCount: Int,
    val unsuccessfulKeys: List<String>,
)
