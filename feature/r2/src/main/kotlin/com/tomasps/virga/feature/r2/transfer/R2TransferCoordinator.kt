package com.tomasps.virga.feature.r2.transfer

import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2ErrorUiMapper
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.api.R2_SINGLE_PUT_MAX_BYTES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs downloads and uploads in the app scope, so a transfer keeps going when the user leaves the screen that
 * started it. It runs only while the process is alive: there is no background service, so the OS may end a
 * transfer when the app is in the background for long. The transfers panel shows every state, including cancelled.
 */
class R2TransferCoordinator(
    private val repository: R2Repository,
    private val streams: R2DocumentStreams,
    private val scope: CoroutineScope,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    private val mutableTransfers = MutableStateFlow<List<R2Transfer>>(emptyList())
    val transfers: StateFlow<List<R2Transfer>> = mutableTransfers.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    /** Starts a download into [destinationUri]. Returns the id to watch in [transfers]. */
    fun startDownload(
        accountId: String,
        bucketName: String,
        objectKey: String,
        destinationUri: String,
        totalBytes: Long?,
    ): String {
        val id = register(accountId, R2TransferDirection.DOWNLOAD, bucketName, objectKey, totalBytes)
        launch(id) { runDownload(id, accountId, bucketName, objectKey, destinationUri, totalBytes) }
        return id
    }

    /** Starts a single PUT of [sourceUri]. Files over the single-PUT limit fail at once with an explanation. */
    fun startUpload(
        accountId: String,
        bucketName: String,
        objectKey: String,
        sourceUri: String,
        contentType: String?,
        totalBytes: Long,
    ): String {
        val id = register(accountId, R2TransferDirection.UPLOAD, bucketName, objectKey, totalBytes)
        if (totalBytes > R2_SINGLE_PUT_MAX_BYTES) {
            finish(id, R2TransferStatus.FAILED, tooLargeFailure(totalBytes))
        } else {
            launch(id) { runUpload(id, accountId, bucketName, objectKey, sourceUri, contentType, totalBytes) }
        }
        return id
    }

    /** Cancels a running transfer. A download's partial document is removed. */
    fun cancel(id: String) {
        jobs[id]?.cancel()
    }

    /** Removes a transfer from the panel. Running transfers are left alone. */
    fun dismiss(id: String) {
        mutableTransfers.update { list ->
            list.filterNot { it.id == id && it.status != R2TransferStatus.RUNNING }
        }
    }

    private suspend fun runDownload(
        id: String,
        accountId: String,
        bucketName: String,
        objectKey: String,
        destinationUri: String,
        totalBytes: Long?,
    ) {
        try {
            val outcome = repository.downloadObject(accountId, bucketName, objectKey) { channel, _ ->
                val throttle = ProgressThrottle(totalBytes)
                streams.openForWrite(destinationUri).use { output ->
                    copyFromChannel(channel, output) { done -> if (throttle.shouldReport(done)) progress(id, done) }
                }
            }
            completeWith(id, outcome, destinationUri)
        } catch (cancelled: CancellationException) {
            streams.delete(destinationUri)
            finish(id, R2TransferStatus.CANCELLED, failure = null)
            throw cancelled
        } catch (error: Exception) {
            streams.delete(destinationUri)
            finish(id, R2TransferStatus.FAILED, failureOf(error))
        }
    }

    private suspend fun runUpload(
        id: String,
        accountId: String,
        bucketName: String,
        objectKey: String,
        sourceUri: String,
        contentType: String?,
        totalBytes: Long,
    ) {
        try {
            val throttle = ProgressThrottle(totalBytes)
            val outcome = repository.uploadObject(accountId, bucketName, objectKey, contentType, totalBytes) { channel ->
                streams.openForRead(sourceUri).use { input ->
                    val copied = copyToChannel(input, channel) { done -> if (throttle.shouldReport(done)) progress(id, done) }
                    if (copied != totalBytes) throw IOException(SIZE_CHANGED_REASON)
                }
            }
            completeWith(id, outcome, failedSource = null)
        } catch (cancelled: CancellationException) {
            finish(id, R2TransferStatus.CANCELLED, failure = null)
            throw cancelled
        } catch (error: Exception) {
            finish(id, R2TransferStatus.FAILED, failureOf(error))
        }
    }

    /** Any failure that is not a cancellation shows as a friendly message; the detail stays out of the panel. */
    private fun failureOf(error: Exception): ErrorUi =
        ApiErrorUiMapper.toErrorUi(ApiError.Local(error.javaClass.simpleName))

    private fun completeWith(id: String, outcome: ApiResult<*>, failedSource: String?) {
        when (outcome) {
            is ApiResult.Success -> finish(id, R2TransferStatus.COMPLETED, failure = null)
            is ApiResult.Failure -> {
                failedSource?.let { streams.delete(it) }
                finish(id, R2TransferStatus.FAILED, R2ErrorUiMapper.toErrorUi(outcome.error))
            }
        }
    }

    private fun register(accountId: String, direction: R2TransferDirection, bucketName: String, objectKey: String, totalBytes: Long?): String {
        val transfer = R2Transfer(
            id = newId(),
            accountId = accountId,
            direction = direction,
            bucketName = bucketName,
            objectKey = objectKey,
            totalBytes = totalBytes,
        )
        mutableTransfers.update { it + transfer }
        return transfer.id
    }

    private fun launch(id: String, block: suspend () -> Unit) {
        val job = scope.launch { block() }
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id) }
    }

    private fun progress(id: String, doneBytes: Long) {
        mutableTransfers.update { list ->
            list.map { if (it.id == id) it.copy(doneBytes = doneBytes) else it }
        }
    }

    private fun finish(id: String, status: R2TransferStatus, failure: ErrorUi?) {
        mutableTransfers.update { list ->
            list.map { transfer ->
                if (transfer.id != id) {
                    transfer
                } else {
                    val done = if (status == R2TransferStatus.COMPLETED) transfer.totalBytes ?: transfer.doneBytes else transfer.doneBytes
                    transfer.copy(status = status, failure = failure, doneBytes = done)
                }
            }
        }
    }

    private companion object {
        const val SIZE_CHANGED_REASON = "the file changed size while it was uploading"
    }

    private fun tooLargeFailure(sizeBytes: Long): ErrorUi = ErrorUi(
        title = UiText.Resource(R.string.r2_upload_too_large_title),
        message = UiText.Resource(R.string.r2_upload_too_large_message, listOf(formatBytes(sizeBytes))),
        retryable = false,
    )
}
