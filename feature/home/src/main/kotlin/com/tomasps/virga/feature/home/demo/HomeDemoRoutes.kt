package com.tomasps.virga.feature.home.demo

import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.feature.home.analytics.WORKERS_OPERATION
import com.tomasps.virga.feature.home.analytics.ZONES_OPERATION

/**
 * Canned answers for Home's own GraphQL datasets. The list endpoints (zones, Workers, Pages, R2, D1, KV) are answered by
 * their owning features, which serve the same answer for every account. So the demo has no empty account, and Home's
 * empty states are reached only in tests.
 */
class HomeDemoRoutes : DemoRouteContributor {
    override fun contribute(registry: DemoRouteRegistry) {
        registry.graphQl(WORKERS_OPERATION) { request -> homeDemoGraphQlReply(request.bodyText) }
        registry.graphQl(ZONES_OPERATION) { request -> homeDemoGraphQlReply(request.bodyText) }
    }
}

/** A demo zone: the same id, name and status the Domains feature serves, so Home's counts match the domain list. */
internal data class DemoZone(val id: String, val name: String, val status: String)

internal val DEMO_ZONES = listOf(
    DemoZone("3f9a1c7e2b4d4e6f8a0b1c2d3e4f5a61", "example.com", "active"),
    DemoZone("7b2e4d6f8a0c1e3b5d7f9a1c3e5b7d92", "example.org", "active"),
    DemoZone("c4d6e8f0a2b4c6d8e0f1a3b5c7d9e0f2", "example.net", "pending"),
)
