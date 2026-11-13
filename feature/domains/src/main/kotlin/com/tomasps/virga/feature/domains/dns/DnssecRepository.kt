package com.tomasps.virga.feature.domains.dns

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DNSSEC status as the reference lists it. Unknown values stay [UNKNOWN] rather than failing the parse. */
enum class DnssecState {
    ACTIVE, PENDING, DISABLED, PENDING_DISABLED, ERROR, UNKNOWN;

    companion object {
        fun fromWire(raw: String?): DnssecState = when (raw?.lowercase()) {
            "active" -> ACTIVE
            "pending" -> PENDING
            "disabled" -> DISABLED
            "pending-disabled" -> PENDING_DISABLED
            "error" -> ERROR
            else -> UNKNOWN
        }
    }
}

/** DNSSEC details. The DS record is what the owner adds at the registrar. */
@Serializable
data class DnssecDetails(
    val status: String? = null,
    val ds: String? = null,
    val digest: String? = null,
    @SerialName("digest_algorithm") val digestAlgorithm: String? = null,
    @SerialName("digest_type") val digestType: String? = null,
    @SerialName("key_tag") val keyTag: Long? = null,
    val algorithm: String? = null,
    @SerialName("key_type") val keyType: String? = null,
    @SerialName("public_key") val publicKey: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
) {
    val state: DnssecState get() = DnssecState.fromWire(status)
}

@Serializable
data class DnssecStatusBody(val status: String)

class DnssecRepository(private val api: CloudflareApi) {

    suspend fun details(zoneId: String): ApiResult<DnssecDetails> =
        api.get(cloudflarePath("zones", zoneId, "dnssec"))

    /** Sends `active` or `disabled`, the two values the reference accepts for `status`. */
    suspend fun setEnabled(zoneId: String, enabled: Boolean): ApiResult<DnssecDetails> = api.patch(
        cloudflarePath("zones", zoneId, "dnssec"),
        DnssecStatusBody(if (enabled) STATUS_ACTIVE else STATUS_DISABLED),
    )

    private companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_DISABLED = "disabled"
    }
}
