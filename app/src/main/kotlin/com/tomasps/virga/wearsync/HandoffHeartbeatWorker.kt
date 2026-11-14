package com.tomasps.virga.wearsync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.TimeUnit

const val HEARTBEAT_INTERVAL_HOURS = 6L

/** Periodically republishes the handoff so the watch's staleness check keeps passing while the phone is signed in. */
class HandoffHeartbeatWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        get<HandoffPublisher>().heartbeat()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "virga-wear-handoff-heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HandoffHeartbeatWorker>(HEARTBEAT_INTERVAL_HOURS, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
