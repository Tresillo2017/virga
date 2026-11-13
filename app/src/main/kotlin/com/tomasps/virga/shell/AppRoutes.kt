package com.tomasps.virga.shell

import com.tomasps.virga.core.navigation.ComputeRoot
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.StorageRoot
import com.tomasps.virga.core.navigation.registerSharedKeys
import com.tomasps.virga.feature.account.registerAccountEntries
import com.tomasps.virga.feature.builds.registerBuildsEntries
import com.tomasps.virga.feature.d1.registerD1Entries
import com.tomasps.virga.feature.domains.registerDomainsEntries
import com.tomasps.virga.feature.home.registerHomeEntries
import com.tomasps.virga.feature.kv.registerKvEntries
import com.tomasps.virga.feature.pages.registerPagesEntries
import com.tomasps.virga.feature.r2.registerR2Entries
import com.tomasps.virga.feature.workers.registerWorkersEntries

/** The one registry for the app. Every feature's keys and screens are added here, and nowhere else. */
internal fun buildRouteRegistry(): RouteRegistry = RouteRegistry().apply {
    registerSharedKeys()
    route(ComputeRoot.serializer()) { ComputeHubRoute() }
    route(StorageRoot.serializer()) { StorageHubRoute() }
    registerHomeEntries(this)
    registerAccountEntries(this)
    registerWorkersEntries(this)
    registerBuildsEntries(this)
    registerPagesEntries(this)
    registerDomainsEntries(this)
    registerR2Entries(this)
    registerD1Entries(this)
    registerKvEntries(this)
}
