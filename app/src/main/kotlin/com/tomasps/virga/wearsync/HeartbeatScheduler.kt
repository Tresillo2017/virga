package com.tomasps.virga.wearsync

import android.content.Context

/** Starts or stops the periodic republish. Both calls are idempotent. */
interface HeartbeatScheduler {
    fun schedule()
    fun cancel()
}

class WorkManagerHeartbeatScheduler(private val context: Context) : HeartbeatScheduler {
    override fun schedule() = HandoffHeartbeatWorker.schedule(context)
    override fun cancel() = HandoffHeartbeatWorker.cancel(context)
}
