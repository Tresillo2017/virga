package com.tomasps.virga.feature.workers.metrics

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WorkerMetricsRepositoryTest {

    @Test
    fun `the query sends the account tag, script name and window and parses the groups`() = runBlocking {
        var sentBody = ""
        val api = mockCloudflareApi {
            post("/graphql") { request ->
                sentBody = request.bodyText.orEmpty()
                DemoReply.graphQl(
                    Json.parseToJsonElement(
                        """{"viewer":{"accounts":[{"workersInvocationsAdaptive":[""" +
                            """{"sum":{"requests":7,"errors":1,"subrequests":2},"quantiles":{"cpuTimeP50":3.0,"cpuTimeP99":9.0},""" +
                            """"dimensions":{"datetime":"2026-11-10T09:00:00Z","scriptName":"edge-api","status":"200"}}]}]}}""",
                    ).jsonObject,
                )
            }
        }
        val start = Instant.parse("2026-11-10T00:00:00Z")
        val end = Instant.parse("2026-11-11T00:00:00Z")

        val result = WorkerMetricsRepository(api).load("acc-1", "edge-api", start, end)

        val body = Json.parseToJsonElement(sentBody).jsonObject
        val variables = body["variables"]!!.jsonObject
        assertEquals("acc-1", variables["accountTag"]!!.jsonPrimitive.content)
        assertEquals("edge-api", variables["scriptName"]!!.jsonPrimitive.content)
        assertEquals("2026-11-10T00:00:00Z", variables["datetimeStart"]!!.jsonPrimitive.content)
        assertEquals("2026-11-11T00:00:00Z", variables["datetimeEnd"]!!.jsonPrimitive.content)
        assertTrue(body["query"]!!.jsonPrimitive.content.contains("workersInvocationsAdaptive"))
        val metrics = (result as ApiResult.Success).value
        assertEquals(7.0, metrics.totalRequests, 0.0)
    }

    @Test
    fun `a failed query is returned as a failure, not an empty success`() = runBlocking {
        val api = mockCloudflareApi {
            post("/graphql") { DemoReply.graphQlErrors("unknown operation") }
        }

        val result = WorkerMetricsRepository(api).load(
            "acc-1",
            "edge-api",
            Instant.parse("2026-11-10T00:00:00Z"),
            Instant.parse("2026-11-11T00:00:00Z"),
        )

        assertTrue(result is ApiResult.Failure)
    }
}
