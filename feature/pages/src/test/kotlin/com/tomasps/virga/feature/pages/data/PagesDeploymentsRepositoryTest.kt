package com.tomasps.virga.feature.pages.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.PageInfo
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class PagesDeploymentsRepositoryTest {

    private val deploymentJson = buildJsonObject {
        put("id", "dep-1")
        put("environment", "production")
        put("latest_stage", buildJsonObject { put("name", "deploy"); put("status", "success") })
    }

    @Test
    fun `the deployment list passes page and per_page and keeps the result_info page data`() = runBlocking {
        // Arrange
        var query = emptyMap<String, String>()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/deployments") { request ->
                query = request.query
                DemoReply.ok(JsonArray(listOf(deploymentJson)), buildJsonObject {
                    put("page", 2); put("per_page", 25); put("count", 1); put("total_count", 26)
                })
            }
        }

        // Act
        val result = PagesDeploymentsRepository(api).page("acc-1", "lantern-docs", page = 2, perPage = 25)

        // Assert
        assertEquals("2", query["page"])
        assertEquals("25", query["per_page"])
        val success = result as ApiResult.Success
        assertEquals("dep-1", success.value.single().id)
        assertEquals(26, (success.pageInfo as PageInfo.Page).totalCount)
    }

    @Test
    fun `retry and rollback each POST to their own path`() = runBlocking {
        val seen = mutableListOf<String>()
        val api = mockCloudflareApi {
            post("/accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}/retry") { request ->
                seen += "retry:" + request.pathParams["deploymentId"]; DemoReply.ok(deploymentJson)
            }
            post("/accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}/rollback") { request ->
                seen += "rollback:" + request.pathParams["deploymentId"]; DemoReply.ok(deploymentJson)
            }
        }
        val repository = PagesDeploymentsRepository(api)

        repository.retry("acc-1", "lantern-docs", "dep-1")
        repository.rollback("acc-1", "lantern-docs", "dep-2")

        assertEquals(listOf("retry:dep-1", "rollback:dep-2"), seen)
    }

    @Test
    fun `delete sends force only when the user asked to delete an aliased deployment`() = runBlocking {
        val queries = mutableListOf<Map<String, String>>()
        val api = mockCloudflareApi {
            delete("/accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}") { request ->
                queries += request.query; DemoReply.ok(JsonNull)
            }
        }
        val repository = PagesDeploymentsRepository(api)

        repository.delete("acc-1", "lantern-docs", "dep-1", force = false)
        repository.delete("acc-1", "lantern-docs", "dep-1", force = true)

        assertEquals(emptyMap<String, String>(), queries[0].filterKeys { it == "force" })
        assertEquals("true", queries[1]["force"])
    }

    @Test
    fun `logs are read from the history logs path and decoded line by line`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}/history/logs") {
                DemoReply.ok(buildJsonObject {
                    put("total", 2)
                    put("includes_container_logs", false)
                    put("data", buildJsonArray {
                        add(buildJsonObject { put("ts", "2026-11-10T10:00:00.0749819Z"); put("line", "Cloning repository...") })
                        add(buildJsonObject { put("ts", "2026-11-10T10:00:01Z"); put("line", "Build complete") })
                    })
                })
            }
        }

        val result = PagesDeploymentsRepository(api).logs("acc-1", "lantern-docs", "dep-1")

        val logs = (result as ApiResult.Success).value
        assertEquals(listOf("Cloning repository...", "Build complete"), logs.data.orEmpty().map { it.line })
        assertEquals(2, logs.total)
    }
}
