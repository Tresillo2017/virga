package com.tomasps.virga.core.network.testing

import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.CredentialProvider
import com.tomasps.virga.core.network.RateLimiter
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import kotlinx.coroutines.Dispatchers

/** Token used by [mockCloudflareApi] unless a test overrides it. Not a real credential. */
const val TEST_TOKEN = "test-token-not-real"

/**
 * One-line [CloudflareApi] backed by MockEngine. Describe routes in [routes]:
 *
 * ```
 * val api = mockCloudflareApi {
 *     get("/accounts/{accountId}/workers/scripts") { DemoReply.ok(JsonArray(emptyList())) }
 * }
 * ```
 */
fun mockCloudflareApi(
    token: String? = TEST_TOKEN,
    rateLimiter: RateLimiter = RateLimiter(),
    routes: DemoRouteRegistry.() -> Unit,
): CloudflareApi {
    val registry = DemoRouteRegistry().apply(routes)
    return CloudflareApi.create(
        credentials = CredentialProvider { token },
        demoRoutes = registry,
        rateLimiter = rateLimiter,
        // Unconfined: answers come back on the caller's scheduler, so runTest's virtual time never waits on a real thread.
        liveEngine = registry.toMockEngine(Dispatchers.Unconfined),
    )
}
