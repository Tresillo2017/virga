package com.tomasps.virga.feature.account.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val DEMO_ACCOUNT_IDS_PERSONAL = "demo-account-personal"
internal const val DEMO_ACCOUNT_IDS_STAGING = "demo-account-staging"
internal val DEMO_ACCOUNT_IDS = listOf(DEMO_ACCOUNT_IDS_PERSONAL, DEMO_ACCOUNT_IDS_STAGING)

private const val DEMO_CREATED_ON = "2024-05-01T09:00:00Z"
private const val NOT_FOUND_CODE = 7003

/**
 * Canned sign-in routes so the demo account can verify and list accounts without a token. The account names are
 * passed in from string resources, so they are localised like the rest of the UI.
 */
class AccountDemoRoutes(
    private val personalName: String,
    private val stagingName: String,
) : DemoRouteContributor {

    private val names: Map<String, String>
        get() = mapOf(DEMO_ACCOUNT_IDS_PERSONAL to personalName, DEMO_ACCOUNT_IDS_STAGING to stagingName)

    override fun contribute(registry: DemoRouteRegistry) {
        registry.get("/user/tokens/verify") {
            DemoReply.ok(buildJsonObject {
                put("id", "demo-token")
                put("status", "active")
            })
        }
        registry.get("/accounts") { DemoReply.ok(accounts()) }
        registry.get("/accounts/{accountId}") { request ->
            val id = request.pathParams["accountId"]
            val name = names[id]
            if (name == null) {
                DemoReply.failure(HttpStatusCode.NotFound, code = NOT_FOUND_CODE, message = "Account not found")
            } else {
                DemoReply.ok(buildJsonObject {
                    put("id", id)
                    put("name", name)
                    put("type", "standard")
                    put("created_on", DEMO_CREATED_ON)
                })
            }
        }
    }

    private fun accounts(): JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("id", DEMO_ACCOUNT_IDS_PERSONAL)
            put("name", personalName)
        })
        add(buildJsonObject {
            put("id", DEMO_ACCOUNT_IDS_STAGING)
            put("name", stagingName)
        })
    }
}
