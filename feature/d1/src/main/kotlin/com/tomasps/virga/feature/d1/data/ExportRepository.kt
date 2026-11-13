package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.coroutines.delay

/** What to include in the dump. Both false means schema and data. */
data class ExportOptions(
    val structureOnly: Boolean = false,
    val dataOnly: Boolean = false,
)

/**
 * SQL dump through the polling export. The database is unavailable for queries while the export runs, and an export
 * that is not polled is cancelled, so [awaitCompletion] keeps polling until the export completes, fails, or runs past
 * [D1Limits.EXPORT_MAX_WAIT_MILLIS]. [pause] is injected so the wait is testable without real time.
 */
class ExportRepository(
    private val api: CloudflareApi,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {

    suspend fun start(accountId: String, databaseId: String, options: ExportOptions): ApiResult<ExportPoll> =
        api.post(exportPath(accountId, databaseId), ExportRequest(outputFormat = ExportRequest.POLLING_FORMAT, dumpOptions = options.toDumpOptions()))

    suspend fun poll(accountId: String, databaseId: String, bookmark: String): ApiResult<ExportPoll> =
        api.post(exportPath(accountId, databaseId), ExportRequest(outputFormat = ExportRequest.POLLING_FORMAT, currentBookmark = bookmark))

    /**
     * Polls from [first] until the export finishes. [onProgress] sees every status so the UI can show progress.
     * A complete export returns its file; the caller must not log or display [ExportFile.signedUrl].
     */
    suspend fun awaitCompletion(
        accountId: String,
        databaseId: String,
        first: ExportPoll,
        onProgress: (ExportPoll) -> Unit,
    ): ApiResult<ExportFile> {
        var current = first
        var waited = 0L
        onProgress(current)
        while (true) {
            completedFile(current)?.let { return it }
            if (current.status == STATUS_ERROR) return ApiResult.Failure(ApiError.Local(EXPORT_FAILED_REASON))
            if (waited >= D1Limits.EXPORT_MAX_WAIT_MILLIS) return ApiResult.Failure(ApiError.Local(TIMED_OUT_REASON))
            val bookmark = current.atBookmark
                ?: return ApiResult.Failure(ApiError.Decode(NO_BOOKMARK_REASON))
            pause(D1Limits.EXPORT_POLL_INTERVAL_MILLIS)
            waited += D1Limits.EXPORT_POLL_INTERVAL_MILLIS
            current = when (val next = poll(accountId, databaseId, bookmark)) {
                is ApiResult.Success -> next.value
                is ApiResult.Failure -> return next
            }
            onProgress(current)
        }
    }

    private fun completedFile(poll: ExportPoll): ApiResult<ExportFile>? {
        if (poll.status != STATUS_COMPLETE) return null
        val file = poll.result
        return if (file?.signedUrl.isNullOrBlank()) {
            ApiResult.Failure(ApiError.Decode(NO_FILE_REASON))
        } else {
            ApiResult.Success(file!!)
        }
    }

    private fun exportPath(accountId: String, databaseId: String): String =
        cloudflarePath("accounts", accountId, "d1", "database", databaseId, "export")

    private fun ExportOptions.toDumpOptions(): DumpOptions? = when {
        structureOnly -> DumpOptions(noData = true)
        dataOnly -> DumpOptions(noSchema = true)
        else -> null
    }

    companion object {
        const val TIMED_OUT_REASON = "Export did not finish in time"
        const val EXPORT_FAILED_REASON = "Export failed"
        private const val NO_BOOKMARK_REASON = "Export response had no bookmark to poll from"
        private const val NO_FILE_REASON = "Export completed without a download link"
        private const val STATUS_COMPLETE = "complete"
        private const val STATUS_ERROR = "error"
    }
}
