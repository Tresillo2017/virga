package com.tomasps.virga.wearsync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.TimeUnit

private const val CLEAR_RETRY_BACKOFF_SECONDS = 30L

/** Retries a failed sign-out clear of the watch's credential, with backoff, until it succeeds. */
class HandoffClearRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result =
        if (get<HandoffPublisher>().retryPendingClear()) Result.success() else Result.retry()

    companion object {
        const val UNIQUE_NAME = "virga-wear-handoff-clear-retry"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<HandoffClearRetryWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CLEAR_RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
