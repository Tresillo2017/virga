package com.tomasps.virga.wear.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.wear.summary.SUMMARY_OPERATION
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val SCRIPTS = "/accounts/{accountId}/workers/scripts"

/** Worker names the demo account lists. The first is the one whose seeded build runs, so the demo has a live build. */
internal val DEMO_SCRIPT_NAMES = listOf("edge-api", "checkout", "image-resizer")

/** The numbers the demo account's 24h summary tile shows. */
internal const val DEMO_SUMMARY_REQUESTS = 48_213L
internal const val DEMO_SUMMARY_ERRORS = 412L

/**
 * The Workers list for the demo account. [com.tomasps.virga.feature.builds.demo.BuildsDemoRoutes] serves the builds
 * but not this list, which lives in the phone's Workers feature. The watch reads the list to find Workers, and does
 * not carry that whole feature just for three names. It also answers the account-level analytics query the summary tile
 * sends, with seeded numbers (Home's own demo GraphQL lives in :feature:home, which the watch does not depend on).
 */
class WearDemoRoutes : DemoRouteContributor {
    override fun contribute(registry: DemoRouteRegistry) {
        registry.get(SCRIPTS) {
            DemoReply.ok(
                buildJsonArray { DEMO_SCRIPT_NAMES.forEach { name -> add(buildJsonObject { put("id", name) }) } },
            )
        }
        registry.graphQl(SUMMARY_OPERATION) { DemoReply.graphQl(demoSummaryData()) }
    }
}

private fun demoSummaryData() = buildJsonObject {
    put("viewer", buildJsonObject {
        put("accounts", buildJsonArray {
            add(buildJsonObject {
                put("totals", buildJsonArray {
                    add(buildJsonObject {
                        put("sum", buildJsonObject {
                            put("requests", DEMO_SUMMARY_REQUESTS)
                            put("errors", DEMO_SUMMARY_ERRORS)
                        })
                    })
                })
            })
        })
    })
}
