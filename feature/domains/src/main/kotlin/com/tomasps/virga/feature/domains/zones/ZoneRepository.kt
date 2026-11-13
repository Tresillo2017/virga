package com.tomasps.virga.feature.domains.zones

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.allPages
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.builtins.ListSerializer

/**
 * Zone reads and writes. Page size 50 is the maximum the reference allows on GET /zones.
 *
 * [changed] fires after a zone was added, paused, resumed or deleted, so the zone list reloads and never shows a
 * zone that no longer exists or a stale status.
 */
class ZoneRepository(private val api: CloudflareApi) {

    private val mutableChanged = MutableSharedFlow<Unit>(extraBufferCapacity = CHANGE_BUFFER)
    val changed: SharedFlow<Unit> = mutableChanged.asSharedFlow()

    /** Every zone in the account. The list is small enough to filter on the device, so no paging UI is needed. */
    suspend fun listAll(accountId: String): ApiResult<List<Zone>> = api.allPages(
        path = cloudflarePath("zones"),
        query = mapOf("account.id" to accountId),
        deserializer = ListSerializer(Zone.serializer()),
        perPage = ZONE_PAGE_SIZE,
    )

    suspend fun get(zoneId: String): ApiResult<Zone> = api.get(cloudflarePath("zones", zoneId))

    suspend fun create(accountId: String, name: String): ApiResult<Zone> = api.post<ZoneCreateBody, Zone>(
        path = cloudflarePath("zones"),
        body = ZoneCreateBody(name = name, account = ZoneAccountRef(accountId), type = ZoneType.FULL),
    ).also { notifyIfSucceeded(it) }

    suspend fun delete(zoneId: String): ApiResult<DeletedZone?> =
        api.delete<DeletedZone?>(cloudflarePath("zones", zoneId)).also { notifyIfSucceeded(it) }

    suspend fun setPaused(zoneId: String, paused: Boolean): ApiResult<Zone> =
        api.patch<ZonePausedBody, Zone>(cloudflarePath("zones", zoneId), ZonePausedBody(paused)).also { notifyIfSucceeded(it) }

    private fun notifyIfSucceeded(result: ApiResult<*>) {
        if (result is ApiResult.Success) mutableChanged.tryEmit(Unit)
    }

    private companion object {
        const val ZONE_PAGE_SIZE = 50
        const val CHANGE_BUFFER = 8
    }
}
