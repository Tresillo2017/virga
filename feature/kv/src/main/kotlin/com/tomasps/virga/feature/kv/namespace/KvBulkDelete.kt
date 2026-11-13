package com.tomasps.virga.feature.kv.namespace

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.data.KvBulkOutcome
import com.tomasps.virga.feature.kv.kvActionMessage
import com.tomasps.virga.feature.kv.value.KV_BULK_MAX_PAIRS

/**
 * What a bulk delete achieved. [deleted] counts the keys the server confirmed before any stop. [failed] holds the
 * keys the server refused. [stoppedBy] is the error of the request that ended the run early, when one did.
 */
data class KvBulkDeleteOutcome(
    val deleted: Int,
    val failed: List<String>,
    val stoppedBy: ApiError?,
)

/**
 * Deletes [names] in chunks of at most [chunkSize], in order. A failed chunk ends the run, but the counts from the
 * chunks before it are kept, so the user is told what was already removed.
 */
internal suspend fun deleteKeysInChunks(
    names: List<String>,
    chunkSize: Int = KV_BULK_MAX_PAIRS,
    deleteChunk: suspend (List<String>) -> ApiResult<KvBulkOutcome>,
): KvBulkDeleteOutcome {
    var deleted = 0
    val failed = mutableListOf<String>()
    for (chunk in names.chunked(chunkSize)) {
        when (val result = deleteChunk(chunk)) {
            is ApiResult.Success -> {
                deleted += result.value.successfulCount
                failed += result.value.unsuccessfulKeys
            }
            is ApiResult.Failure -> return KvBulkDeleteOutcome(deleted, failed.toList(), result.error)
        }
    }
    return KvBulkDeleteOutcome(deleted, failed.toList(), stoppedBy = null)
}

/** The one message shown after a bulk delete. A stopped run reports how many keys it removed before it stopped. */
internal fun bulkDeleteMessage(outcome: KvBulkDeleteOutcome): UiText {
    val error = outcome.stoppedBy
    return when {
        error != null && outcome.deleted == 0 -> kvActionMessage(error, isSingleKeyWrite = false)
        error != null -> UiText.Resource(R.string.kv_message_bulk_stopped, listOf(outcome.deleted))
        outcome.failed.isEmpty() -> UiText.Resource(R.string.kv_message_bulk_deleted, listOf(outcome.deleted))
        else -> UiText.Resource(R.string.kv_message_bulk_partial, listOf(outcome.deleted, outcome.failed.size))
    }
}
