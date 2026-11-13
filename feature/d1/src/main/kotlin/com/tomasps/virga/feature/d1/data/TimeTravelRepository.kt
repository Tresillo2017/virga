package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.cloudflarePath

/**
 * Time Travel: read bookmarks and restore. Restore overwrites the database in place and cancels in-flight queries,
 * so the caller must confirm it with a typed name first. The bookmark or timestamp travels as a query parameter,
 * as the reference documents it.
 */
class TimeTravelRepository(private val api: CloudflareApi) {

    suspend fun currentBookmark(accountId: String, databaseId: String): ApiResult<BookmarkResult> =
        api.get(timeTravelPath(accountId, databaseId, BOOKMARK_SEGMENT))

    /** The bookmark at or before [isoTimestamp], an ISO 8601 date-time such as 2026-11-10T08:00:00Z. */
    suspend fun bookmarkAt(accountId: String, databaseId: String, isoTimestamp: String): ApiResult<BookmarkResult> =
        api.get(timeTravelPath(accountId, databaseId, BOOKMARK_SEGMENT), mapOf(TIMESTAMP to isoTimestamp))

    suspend fun restoreToBookmark(accountId: String, databaseId: String, bookmark: String): ApiResult<RestoreResult> =
        api.postEmpty(timeTravelPath(accountId, databaseId, RESTORE_SEGMENT), mapOf(BOOKMARK to bookmark))

    suspend fun restoreToTimestamp(accountId: String, databaseId: String, isoTimestamp: String): ApiResult<RestoreResult> =
        api.postEmpty(timeTravelPath(accountId, databaseId, RESTORE_SEGMENT), mapOf(TIMESTAMP to isoTimestamp))

    private fun timeTravelPath(accountId: String, databaseId: String, action: String): String =
        cloudflarePath("accounts", accountId, "d1", "database", databaseId, "time_travel", action)

    private companion object {
        const val BOOKMARK_SEGMENT = "bookmark"
        const val RESTORE_SEGMENT = "restore"
        const val BOOKMARK = "bookmark"
        const val TIMESTAMP = "timestamp"
    }
}
