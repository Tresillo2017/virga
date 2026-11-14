package com.tomasps.virga.wear.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val SCRIPTS = "/accounts/{accountId}/workers/scripts"

/** Worker names the demo account lists. The first is the one whose seeded build runs, so the demo has a live build. */
internal val DEMO_SCRIPT_NAMES = listOf("edge-api", "checkout", "image-resizer")

/**
 * The Workers list for the demo account. [com.tomasps.virga.feature.builds.demo.BuildsDemoRoutes] serves the builds
 * but not this list, which lives in the phone's Workers feature. The watch reads the list to find Workers, and does
 * not carry that whole feature just for three names.
 */
class WearDemoRoutes : DemoRouteContributor {
    override fun contribute(registry: DemoRouteRegistry) {
        registry.get(SCRIPTS) {
            DemoReply.ok(
                buildJsonArray { DEMO_SCRIPT_NAMES.forEach { name -> add(buildJsonObject { put("id", name) }) } },
            )
        }
    }
}
