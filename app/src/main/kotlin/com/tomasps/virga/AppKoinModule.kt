package com.tomasps.virga

import com.tomasps.virga.core.navigation.PendingNavigation
import org.koin.dsl.module

/** App-level bindings: the holder for links that arrive from intents and notifications. */
val appKoinModule = module {
    single { PendingNavigation() }
}
