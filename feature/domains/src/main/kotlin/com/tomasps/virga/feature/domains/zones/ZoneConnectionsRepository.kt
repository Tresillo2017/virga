package com.tomasps.virga.feature.domains.zones

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A zone route as GET /zones/{id}/workers/routes returns it. [script] is null for a route with no Worker attached. */
@Serializable
data class WorkerRoute(
    val id: String = "",
    val pattern: String = "",
    val script: String? = null,
)

/** Registrar data for a domain bought through Cloudflare Registrar. */
@Serializable
data class RegistrarRegistration(
    @SerialName("domain_name") val domainName: String = "",
    val status: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("auto_renew") val autoRenew: Boolean? = null,
    @SerialName("privacy_mode") val privacyMode: String? = null,
    val locked: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Routes and registrar data for a zone. */
class ZoneConnectionsRepository(private val api: CloudflareApi) {

    suspend fun routes(zoneId: String): ApiResult<List<WorkerRoute>> =
        api.get(cloudflarePath("zones", zoneId, "workers", "routes"))

    /**
     * Returns the registration, or a NotFound failure when the domain was not bought through Cloudflare. The caller
     * treats NotFound as "not registered here", not as an error.
     */
    suspend fun registration(accountId: String, domain: String): ApiResult<RegistrarRegistration> =
        api.get(cloudflarePath("accounts", accountId, "registrar", "registrations", domain))
}
