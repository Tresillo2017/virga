package com.tomasps.virga.wearsync

import android.content.Context
import android.util.Log
import com.tomasps.virga.core.wearsync.HandoffTransport
import org.koin.dsl.module

/** Phone-side Wear handoff. Needs an Android Context (androidContext() in the app). */
val wearSyncKoinModule = module {
    single<HandoffTransport> { GmsHandoffTransport(get()) }
    single<PendingClearStore> { SharedPrefsPendingClearStore(get()) }
    single<WatchAppFinder> { GmsWatchAppFinder(get()) }
    single<HeartbeatScheduler> { WorkManagerHeartbeatScheduler(get()) }
    single {
        val context = get<Context>()
        HandoffPublisher(
            session = get(),
            vault = get(),
            transport = get(),
            clock = System::currentTimeMillis,
            pendingClear = get(),
            watchApp = get(),
            heartbeat = get(),
            scheduleClearRetry = { HandoffClearRetryWorker.schedule(context) },
            log = { Log.w("VirgaWear", it) },
            isUnavailable = ::isWearApiUnavailable,
        )
    }
}
