package com.tomasps.virga.feature.pages.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagesProjectsRepositoryTest {

    private val projectJson = buildJsonObject {
        put("id", "p-1")
        put("name", "lantern-docs")
        put("production_branch", "main")
        put("subdomain", "lantern-docs.pages.dev")
        put("unknown_future_field", true)
    }

    @Test
    fun `projects are listed from the account pages path, ignoring unknown fields`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects") { DemoReply.ok(JsonArray(listOf(projectJson))) }
        }

        // Act
        val result = PagesProjectsRepository(api).projects("acc-1")

        // Assert
        val names = (result as ApiResult.Success).value.map { it.name }
        assertEquals(listOf("lantern-docs"), names)
    }

    @Test
    fun `settings update sends exactly the body it is given, as a PATCH to the project`() = runBlocking {
        // Arrange
        var sentMethod = ""
        var sentBody = ""
        val api = mockCloudflareApi {
            patch("/accounts/{accountId}/pages/projects/{projectName}") { request ->
                sentMethod = request.method.value
                sentBody = request.bodyText.orEmpty()
                DemoReply.ok(projectJson)
            }
        }
        val body = buildJsonObject { put("production_branch", "release") }

        // Act
        val result = PagesProjectsRepository(api).updateSettings("acc-1", "lantern-docs", body)

        // Assert
        assertEquals("PATCH", sentMethod)
        assertEquals("""{"production_branch":"release"}""", sentBody)
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `a null key in a settings patch is sent as JSON null, so the server removes that variable`() = runBlocking {
        var sentBody = ""
        val api = mockCloudflareApi {
            patch("/accounts/{accountId}/pages/projects/{projectName}") { request ->
                sentBody = request.bodyText.orEmpty()
                DemoReply.ok(projectJson)
            }
        }
        val body = buildJsonObject {
            putJsonObject("deployment_configs") {
                putJsonObject("production") { putJsonObject("env_vars") { put("OLD", JsonNull) } }
            }
        }

        PagesProjectsRepository(api).updateSettings("acc-1", "lantern-docs", body)

        assertEquals("""{"deployment_configs":{"production":{"env_vars":{"OLD":null}}}}""", sentBody)
    }

    @Test
    fun `deleting a project accepts the null result envelope`() = runBlocking {
        val api = mockCloudflareApi {
            delete("/accounts/{accountId}/pages/projects/{projectName}") { DemoReply.ok(JsonNull) }
        }

        val result = PagesProjectsRepository(api).deleteProject("acc-1", "lantern-docs")

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `purging the build cache posts to its path`() = runBlocking {
        var sentMethod = ""
        val api = mockCloudflareApi {
            post("/accounts/{accountId}/pages/projects/{projectName}/purge_build_cache") { request ->
                sentMethod = request.method.value
                DemoReply.ok(JsonNull)
            }
        }

        val result = PagesProjectsRepository(api).purgeBuildCache("acc-1", "lantern-docs")

        assertEquals("POST", sentMethod)
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `a missing project surfaces as a not-found failure`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}") {
                DemoReply.failure(HttpStatusCode.NotFound, code = 8000007, message = "not found")
            }
        }

        val result = PagesProjectsRepository(api).project("acc-1", "missing")

        assertTrue(result is ApiResult.Failure)
    }
}
