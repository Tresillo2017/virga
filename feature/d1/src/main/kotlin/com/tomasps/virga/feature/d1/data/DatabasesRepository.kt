package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Database-level calls: list, create, read, delete, and the read-replication setting. */
class DatabasesRepository(private val api: CloudflareApi) {

    suspend fun list(accountId: String, page: Int): ApiResult<List<D1Database>> = api.get(
        path = cloudflarePath("accounts", accountId, "d1", "database"),
        query = mapOf("page" to page.toString(), "per_page" to D1Limits.LIST_PAGE_SIZE.toString()),
    )

    suspend fun create(accountId: String, body: CreateDatabaseBody): ApiResult<D1Database> =
        api.post(cloudflarePath("accounts", accountId, "d1", "database"), body)

    suspend fun get(accountId: String, databaseId: String): ApiResult<D1Database> =
        api.get(databasePath(accountId, databaseId))

    suspend fun delete(accountId: String, databaseId: String): ApiResult<Unit> =
        api.delete(databasePath(accountId, databaseId))

    /**
     * Restores a database deleted within the last [D1Limits.UNDELETE_WINDOW_MILLIS]. [newName] is needed only when
     * another database in the account already uses the original name.
     */
    suspend fun undelete(accountId: String, databaseId: String, newName: String? = null): ApiResult<D1Database> {
        val body = buildJsonObject { if (newName != null) put("new_name", newName) }
        return api.post(cloudflarePath("accounts", accountId, "d1", "database", databaseId, "undelete"), body)
    }

    /**
     * PUT, as the reference documents it (PATCH is not in the reference). The database name cannot be changed
     * through the API, so only read_replication.mode is sent.
     */
    suspend fun setReadReplication(accountId: String, databaseId: String, mode: String): ApiResult<D1Database> =
        api.put(databasePath(accountId, databaseId), ReadReplicationUpdate(ReadReplication(mode = mode)))
}

internal fun databasePath(accountId: String, databaseId: String): String =
    cloudflarePath("accounts", accountId, "d1", "database", databaseId)
