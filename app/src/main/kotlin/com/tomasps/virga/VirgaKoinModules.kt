package com.tomasps.virga

import com.tomasps.virga.core.auth.coreAuthKoinModule
import com.tomasps.virga.core.common.coreCommonKoinModule
import com.tomasps.virga.core.network.coreNetworkKoinModule
import com.tomasps.virga.feature.account.accountKoinModule
import com.tomasps.virga.feature.builds.buildsKoinModule
import com.tomasps.virga.feature.d1.d1KoinModule
import com.tomasps.virga.feature.domains.domainsKoinModule
import com.tomasps.virga.feature.home.homeKoinModule
import com.tomasps.virga.feature.kv.kvKoinModule
import com.tomasps.virga.feature.pages.pagesKoinModule
import com.tomasps.virga.feature.r2.r2KoinModule
import com.tomasps.virga.feature.workers.workersKoinModule
import org.koin.core.module.Module

/** Every Koin module the app starts with, in start order. [VirgaApplication] and the graph test both read it. */
val virgaKoinModules: List<Module> = listOf(
    appKoinModule,
    coreCommonKoinModule,
    coreNetworkKoinModule,
    coreAuthKoinModule,
    accountKoinModule,
    homeKoinModule,
    workersKoinModule,
    buildsKoinModule,
    pagesKoinModule,
    domainsKoinModule,
    r2KoinModule,
    d1KoinModule,
    kvKoinModule,
)
