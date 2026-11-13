package com.tomasps.virga.feature.domains.dns

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the zone answers DNS queries. `dns_only` and `cdn_only` turn off proxying for every record in the zone. */
enum class ZoneMode { STANDARD, CDN_ONLY, DNS_ONLY, UNKNOWN;

    companion object {
        /** Maps the wire value. Anything the app does not know becomes [UNKNOWN] rather than failing the read. */
        fun fromApi(raw: String?): ZoneMode = when (raw) {
            "standard" -> STANDARD
            "cdn_only" -> CDN_ONLY
            "dns_only" -> DNS_ONLY
            else -> UNKNOWN
        }
    }
}

/** Zone-level DNS settings as the reference returns them. Every field is optional so an unexpected shape still parses. */
data class ZoneDnsSettings(
    val zoneMode: ZoneMode,
    val nameserverType: String?,
    val flattenAllCnames: Boolean?,
    val multiProvider: Boolean?,
    val nsTtl: Long?,
    val secondaryOverrides: Boolean?,
)

@Serializable
internal data class DnsSettingsDto(
    @SerialName("zone_mode") val zoneMode: String? = null,
    val nameservers: NameserversDto? = null,
    @SerialName("flatten_all_cnames") val flattenAllCnames: Boolean? = null,
    @SerialName("multi_provider") val multiProvider: Boolean? = null,
    @SerialName("ns_ttl") val nsTtl: Long? = null,
    @SerialName("secondary_overrides") val secondaryOverrides: Boolean? = null,
)

@Serializable
internal data class NameserversDto(val type: String? = null)

/** Reads the zone's DNS settings (GET /zones/{zone_id}/dns_settings). Changing them stays in the dashboard. */
class ZoneDnsSettingsRepository(private val api: CloudflareApi) {

    suspend fun settings(zoneId: String): ApiResult<ZoneDnsSettings> =
        api.get<DnsSettingsDto>(cloudflarePath("zones", zoneId, "dns_settings")).map { it.toSettings() }
}

internal fun DnsSettingsDto.toSettings(): ZoneDnsSettings = ZoneDnsSettings(
    zoneMode = ZoneMode.fromApi(zoneMode),
    nameserverType = nameservers?.type,
    flattenAllCnames = flattenAllCnames,
    multiProvider = multiProvider,
    nsTtl = nsTtl,
    secondaryOverrides = secondaryOverrides,
)
