package com.tomasps.virga.wearsync

import com.tomasps.virga.core.wearsync.HandoffTransport
import org.koin.dsl.module

/** Phone-side Wear handoff. Needs an Android Context (androidContext() in the app). */
val wearSyncKoinModule = module {
    single<HandoffTransport> { GmsHandoffTransport(get()) }
    single { HandoffPublisher(get(), get(), get(), System::currentTimeMillis) }
}
