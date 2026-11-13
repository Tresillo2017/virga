package com.tomasps.virga.feature.domains.zones

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The development_mode zone setting. [value] and [timeRemaining] are kept as raw JSON because the reference
 * returns `false` for time_remaining when the mode was never enabled, and the value type is not fixed.
 */
@Serializable
data class DevelopmentModeSetting(
    val id: String? = null,
    val value: JsonElement? = null,
    val editable: Boolean? = null,
    val time_remaining: JsonElement? = null,
) {
    val isOn: Boolean get() = primitiveText(value) == DEV_MODE_ON

    /** Seconds left before the mode turns itself off, or null when the mode is not running. */
    val remainingSeconds: Long?
        get() = (time_remaining as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }

    private fun primitiveText(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

    companion object {
        const val DEV_MODE_ON = "on"
        const val DEV_MODE_OFF = "off"
    }
}

@Serializable
data class DevelopmentModeBody(val value: String)

@Serializable
data class PurgeResult(val id: String? = null)

/** Zone-scoped settings: development mode and cache purge. */
class ZoneSettingsRepository(private val api: CloudflareApi) {

    suspend fun developmentMode(zoneId: String): ApiResult<DevelopmentModeSetting> =
        api.get(cloudflarePath("zones", zoneId, "settings", "development_mode"))

    /** The reference documents the setting as `on` or `off`. The body shape `{"value": ...}` follows the settings API. */
    suspend fun setDevelopmentMode(zoneId: String, on: Boolean): ApiResult<DevelopmentModeSetting> = api.patch(
        cloudflarePath("zones", zoneId, "settings", "development_mode"),
        DevelopmentModeBody(if (on) DevelopmentModeSetting.DEV_MODE_ON else DevelopmentModeSetting.DEV_MODE_OFF),
    )

    suspend fun purge(zoneId: String, request: PurgeRequest): ApiResult<PurgeResult?> =
        api.post<JsonObject, PurgeResult?>(cloudflarePath("zones", zoneId, "purge_cache"), request.toJson())
}
