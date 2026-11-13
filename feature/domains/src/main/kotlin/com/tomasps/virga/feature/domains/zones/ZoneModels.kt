package com.tomasps.virga.feature.domains.zones

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val FREE_PLAN_PREFIX = "Free"

/** One zone as GET /zones and GET /zones/{id} return it. Fields the reference does not require default. */
@Serializable
data class Zone(
    val id: String,
    val name: String,
    val status: String? = null,
    val paused: Boolean = false,
    val type: String? = null,
    @SerialName("name_servers") val nameServers: List<String> = emptyList(),
    @SerialName("original_registrar") val originalRegistrar: String? = null,
    /** Seconds until development mode expires; negative once expired, 0 when never enabled. */
    @SerialName("development_mode") val developmentModeSeconds: Long = 0,
    val account: ZoneAccount? = null,
    /** Deprecated in the reference, but still returned and the only plan signal available here. */
    val plan: ZonePlan? = null,
) {
    /** Free plans have a 100 character comment limit and no record tags. Unknown plans are treated as Free. */
    val isPaidPlan: Boolean
        get() = plan?.name?.let { !it.startsWith(FREE_PLAN_PREFIX, ignoreCase = true) } ?: false

}

@Serializable
data class ZoneAccount(val id: String = "", val name: String = "")

@Serializable
data class ZonePlan(val id: String? = null, val name: String? = null)

/** Body of POST /zones. The reference marks `name` as the only required field. */
@Serializable
data class ZoneCreateBody(val name: String, val account: ZoneAccountRef, val type: String)

@Serializable
data class ZoneAccountRef(val id: String)

/** Body of PATCH /zones/{id} for pausing. The reference returns the updated zone. */
@Serializable
data class ZonePausedBody(val paused: Boolean)

/** Result of DELETE /zones/{id}. The reference returns an object whose id may be absent in some responses. */
@Serializable
data class DeletedZone(val id: String? = null)

/** Zone statuses the reference lists. Anything else is shown as its raw text. */
object ZoneStatus {
    const val ACTIVE = "active"
    const val PENDING = "pending"
    const val INITIALIZING = "initializing"
    const val MOVED = "moved"
    val filterable: List<String> = listOf(ACTIVE, PENDING, INITIALIZING, MOVED)
}

/** Zone types accepted by POST /zones. */
object ZoneType {
    const val FULL = "full"
    const val PARTIAL = "partial"
}
