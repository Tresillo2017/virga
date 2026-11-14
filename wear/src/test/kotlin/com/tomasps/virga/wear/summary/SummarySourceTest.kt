package com.tomasps.virga.wear.summary

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.wear.demo.DEMO_SUMMARY_ERRORS
import com.tomasps.virga.wear.demo.DEMO_SUMMARY_REQUESTS
import com.tomasps.virga.wear.demo.WearDemoRoutes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SummarySourceTest {

    private val now = Instant.parse("2026-11-14T12:00:00Z")

    private fun reply(totals: String) = DemoReply.graphQl(
        Json.parseToJsonElement("""{"viewer":{"accounts":[{"totals":$totals}]}}"""),
    )

    private fun source(
        api: CloudflareApi,
        hasToken: Boolean = true,
        onWipe: () -> Unit = {},
    ) = SummarySource(api.graphQl, onUnauthorized = { onWipe() }, hasToken = { hasToken }, warn = {})

    private fun apiAnswering(handler: () -> DemoReply) = mockCloudflareApi {
        graphQl(SUMMARY_OPERATION) { handler() }
    }

    @Test
    fun parsesRequestsAndErrors() = runTest {
        val api = apiAnswering { reply("""[{"sum":{"requests":1200,"errors":34}}]""") }

        assertEquals(SummaryResult.Ok(SummaryData(1200, 34)), source(api).last24h("acc", now))
    }

    @Test
    fun emptyResultIsZeroNotFailure() = runTest {
        val api = apiAnswering { reply("[]") }

        assertEquals(SummaryResult.Ok(SummaryData(0, 0)), source(api).last24h("acc", now))
    }

    @Test
    fun asksForTheLast24HoursOfTheGivenAccount() = runTest {
        var variables: JsonObject? = null
        val api = mockCloudflareApi {
            graphQl(SUMMARY_OPERATION) { request ->
                variables = Json.parseToJsonElement(request.bodyText!!).jsonObject["variables"]!!.jsonObject
                reply("[]")
            }
        }

        source(api).last24h("acc-9", now)

        assertEquals("acc-9", variables!!["accountTag"]!!.jsonPrimitive.content)
        assertEquals("2026-11-13T12:00:00Z", variables!!["start"]!!.jsonPrimitive.content)
        assertEquals("2026-11-14T12:00:00Z", variables!!["end"]!!.jsonPrimitive.content)
    }

    @Test
    fun authorizationErrorMapsToMissingAnalyticsAccess() = runTest {
        var wiped = 0
        val api = apiAnswering { DemoReply.graphQlErrors("not authorized to access this account's analytics") }

        val result = source(api, onWipe = { wiped++ }).last24h("acc", now)

        assertEquals(SummaryResult.MissingAnalyticsAccess, result)
        assertEquals("a GraphQL authorization error is not a rejected token", 0, wiped)
    }

    @Test
    fun forbiddenStatusMapsToMissingAnalyticsAccess() = runTest {
        val api = apiAnswering { DemoReply.failure(HttpStatusCode.Forbidden, 10000, "forbidden") }

        assertEquals(SummaryResult.MissingAnalyticsAccess, source(api).last24h("acc", now))
    }

    @Test
    fun rateLimitWordingIsNotAnAccessProblem() = runTest {
        val api = apiAnswering { DemoReply.graphQlErrors("rate limit exceeded, permission to retry later") }

        val result = source(api).last24h("acc", now)

        assertTrue(result is SummaryResult.Failed && result.error is ApiError.GraphQl)
    }

    @Test
    fun networkFailureMapsToFailed() = runTest {
        val api = apiAnswering { throw java.io.IOException("offline") }

        val result = source(api).last24h("acc", now)

        assertTrue(result is SummaryResult.Failed)
    }

    @Test
    fun serverErrorMapsToFailed() = runTest {
        val api = apiAnswering { DemoReply.failure(HttpStatusCode.ServiceUnavailable, 1, "down") }

        val result = source(api).last24h("acc", now)

        assertTrue(result is SummaryResult.Failed && result.error is ApiError.Server)
    }

    @Test
    fun malformedDataMapsToFailedDecode() = runTest {
        val api = apiAnswering { DemoReply.graphQl(Json.parseToJsonElement("{}")) }

        val result = source(api).last24h("acc", now)

        assertTrue(result is SummaryResult.Failed && result.error is ApiError.Decode)
    }

    @Test
    fun rejectedTokenWipesTheSessionOnce() = runTest {
        var wiped = 0
        val api = apiAnswering { DemoReply.failure(HttpStatusCode.Unauthorized, 9109, "bad token") }

        val result = source(api, onWipe = { wiped++ }).last24h("acc", now)

        assertTrue(result is SummaryResult.Failed && result.error is ApiError.Unauthorized)
        assertEquals(1, wiped)
    }

    @Test
    fun unauthorizedWithoutATokenDoesNotWipe() = runTest {
        var wiped = 0
        val api = apiAnswering { DemoReply.failure(HttpStatusCode.Unauthorized, 9109, "no token") }

        source(api, hasToken = false, onWipe = { wiped++ }).last24h("acc", now)

        assertEquals(0, wiped)
    }

    @Test
    fun demoEngineReturnsSeededNumbers() = runTest {
        val api = mockCloudflareApi { WearDemoRoutes().contribute(this) }

        val result = source(api).last24h("demo-account", now)

        assertEquals(SummaryResult.Ok(SummaryData(DEMO_SUMMARY_REQUESTS, DEMO_SUMMARY_ERRORS)), result)
    }
}
