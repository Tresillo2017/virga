package com.tomasps.virga.core.common

import org.koin.dsl.module

val coreCommonKoinModule = module {
    single<DispatcherProvider> { StandardDispatcherProvider() }
}
