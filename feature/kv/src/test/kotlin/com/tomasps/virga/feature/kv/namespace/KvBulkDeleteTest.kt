package com.tomasps.virga.feature.kv.namespace

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.data.KvBulkOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val SMALL_CHUNK = 2

class KvBulkDeleteTest {

    @Test
    fun `every chunk is sent in order and the counts add up`() = runBlocking {
        // Arrange
        val sent = mutableListOf<List<String>>()
        val names = listOf("a", "b", "c", "d", "e")

        // Act
        val outcome = deleteKeysInChunks(names, chunkSize = SMALL_CHUNK) { chunk ->
            sent += chunk
            ApiResult.Success(KvBulkOutcome(successfulCount = chunk.size, unsuccessfulKeys = emptyList()))
        }

        // Assert
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e")), sent)
        assertEquals(5, outcome.deleted)
        assertNull(outcome.stoppedBy)
    }

    @Test
    fun `a failing later chunk keeps the count of the chunks before it`() = runBlocking {
        // Arrange
        var call = 0
        val names = listOf("a", "b", "c", "d", "e")

        // Act
        val outcome = deleteKeysInChunks(names, chunkSize = SMALL_CHUNK) { chunk ->
            call++
            if (call == 2) {
                ApiResult.Failure(ApiError.Server(status = 500))
            } else {
                ApiResult.Success(KvBulkOutcome(successfulCount = chunk.size, unsuccessfulKeys = emptyList()))
            }
        }

        // Assert
        assertEquals(2, outcome.deleted)
        assertEquals(ApiError.Server(status = 500), outcome.stoppedBy)
    }

    @Test
    fun `keys the server refuses are collected across chunks`() = runBlocking {
        // Arrange
        val names = listOf("a", "b", "c")

        // Act
        val outcome = deleteKeysInChunks(names, chunkSize = SMALL_CHUNK) { chunk ->
            ApiResult.Success(KvBulkOutcome(successfulCount = 1, unsuccessfulKeys = listOf(chunk.last())))
        }

        // Assert
        assertEquals(listOf("b", "c"), outcome.failed)
        assertEquals(2, outcome.deleted)
    }

    @Test
    fun `a stopped run that already removed keys says how many`() {
        // Arrange
        val outcome = KvBulkDeleteOutcome(deleted = 3, failed = emptyList(), stoppedBy = ApiError.Server(status = 500))

        // Act
        val message = bulkDeleteMessage(outcome)

        // Assert
        assertEquals(R.string.kv_message_bulk_stopped, (message as UiText.Resource).id)
        assertEquals(listOf(3), message.args)
    }

    @Test
    fun `a run that stopped before removing anything reports the failure itself`() {
        // Arrange
        val outcome = KvBulkDeleteOutcome(deleted = 0, failed = emptyList(), stoppedBy = ApiError.Server(status = 500))

        // Act
        val message = bulkDeleteMessage(outcome)

        // Assert
        assertEquals(false, (message as? UiText.Resource)?.id == R.string.kv_message_bulk_stopped)
    }

    @Test
    fun `a clean run reports the deleted count`() {
        // Act
        val message = bulkDeleteMessage(KvBulkDeleteOutcome(deleted = 4, failed = emptyList(), stoppedBy = null))

        // Assert
        assertEquals(R.string.kv_message_bulk_deleted, (message as UiText.Resource).id)
        assertEquals(listOf(4), message.args)
    }
}
