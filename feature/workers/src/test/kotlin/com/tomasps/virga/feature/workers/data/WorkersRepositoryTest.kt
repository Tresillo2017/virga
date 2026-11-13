package com.tomasps.virga.feature.workers.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.junit.Assert.assertEquals
import com.tomasps.virga.feature.workers.testing.assertIs
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class WorkersRepositoryTest {

    @Test
    fun `scripts are listed from the account scripts path and unknown fields are ignored`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") { request ->
                assertEquals("acc-1", request.pathParams["accountId"])
                DemoReply.ok(buildJsonArray {
                    add(buildJsonObject { put("id", "edge-api"); put("future_field", "x") })
                })
            }
        }

        val result = WorkersRepository(api).listScripts("acc-1")

        assertEquals("edge-api", (result as ApiResult.Success).value.single().id)
    }

    @Test
    fun `deployments are decoded from the deployments object and keep version percentages`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts/{scriptName}/deployments") { request ->
                assertEquals("edge-api", request.pathParams["scriptName"])
                DemoReply.ok(buildJsonObject {
                    put("deployments", buildJsonArray {
                        add(buildJsonObject {
                            put("id", "dep-1")
                            put("versions", buildJsonArray {
                                add(buildJsonObject { put("version_id", "v-2"); put("percentage", 80.0) })
                                add(buildJsonObject { put("version_id", "v-1"); put("percentage", 20.0) })
                            })
                        })
                    })
                })
            }
        }

        val page = (WorkersRepository(api).deployments("acc-1", "edge-api") as ApiResult.Success).value

        assertEquals(listOf(80.0, 20.0), page.deployments.single().versions.map { it.percentage })
    }

    @Test
    fun `a percentage deployment posts the strategy and the version split`() = runBlocking {
        var sentBody = ""
        val api = mockCloudflareApi {
            post("/accounts/{accountId}/workers/scripts/{scriptName}/deployments") { request ->
                sentBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("id", "dep-9") })
            }
        }

        val result = WorkersRepository(api).deployPercentages(
            "acc-1",
            "edge-api",
            listOf(DeploymentVersion("v-2", 100.0)),
        )

        assertIs<ApiResult.Success<Deployment>>(result)
        assertTrue(sentBody, sentBody.contains("\"strategy\":\"percentage\""))
        assertTrue(sentBody, sentBody.contains("\"version_id\":\"v-2\""))
    }

    @Test
    fun `secret values are sent once as text and deletions are sent as null`() = runBlocking {
        var sentBody = ""
        var sentMethod: HttpMethod? = null
        val api = mockCloudflareApi {
            patch("/accounts/{accountId}/workers/scripts/{scriptName}/secrets-bulk") { request ->
                sentMethod = request.method
                sentBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("API_KEY", JsonPrimitive("hidden")) })
            }
        }

        val result = WorkersRepository(api).updateSecrets(
            "acc-1",
            "edge-api",
            mapOf("API_KEY" to "new-value", "OLD" to null),
        )

        assertIs<ApiResult.Success<Unit>>(result)
        assertEquals(HttpMethod.Patch, sentMethod)
        assertTrue(sentBody, sentBody.contains("\"text\":\"new-value\""))
        assertTrue(sentBody, sentBody.contains("\"OLD\":null"))
    }

    @Test
    fun `subdomain state reads both flags and tolerates a missing previews field`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts/{scriptName}/subdomain") {
                DemoReply.ok(buildJsonObject { put("enabled", true) })
            }
        }

        val state = (WorkersRepository(api).subdomain("acc-1", "edge-api") as ApiResult.Success).value

        assertTrue(state.enabled)
        assertEquals(false, state.previewsEnabled)
    }

    @Test
    fun `adding a custom domain puts the hostname and service to the domains path`() = runBlocking {
        var sentMethod: HttpMethod? = null
        var sentBody = ""
        val api = mockCloudflareApi {
            put("/accounts/{accountId}/workers/domains") { request ->
                sentMethod = request.method
                sentBody = request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject {
                    put("id", "d-2")
                    put("hostname", "api.example.org")
                    put("service", "edge-api")
                    put("enabled", true)
                })
            }
        }

        val domain = (WorkersRepository(api).addCustomDomain("acc-1", "api.example.org", "edge-api") as ApiResult.Success).value

        assertEquals(HttpMethod.Put, sentMethod)
        assertTrue(sentBody, sentBody.contains("\"hostname\":\"api.example.org\""))
        assertTrue(sentBody, sentBody.contains("\"service\":\"edge-api\""))
        assertEquals("d-2", domain.id)
    }

    @Test
    fun `versions are read from the items array, newest first, and deployable only`() = runBlocking {
        var deployableQuery: String? = null
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts/{scriptName}/versions") { request ->
                deployableQuery = request.query["deployable"]
                DemoReply.ok(buildJsonObject {
                    put("items", buildJsonArray {
                        add(buildJsonObject {
                            put("id", "v-3")
                            put("number", 3)
                            put("metadata", buildJsonObject { put("created_on", "2026-11-10T10:00:00Z"); put("source", "api") })
                        })
                    })
                })
            }
        }

        val versions = (WorkersRepository(api).versions("acc-1", "edge-api") as ApiResult.Success).value

        assertEquals("true", deployableQuery)
        assertEquals("v-3", versions.single().id)
        assertEquals(3, versions.single().number)
    }

    @Test
    fun `custom domains are filtered by the service name`() = runBlocking {
        var service: String? = null
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/domains") { request ->
                service = request.query["service"]
                DemoReply.ok(buildJsonArray {
                    add(buildJsonObject { put("id", "d-1"); put("hostname", "api.example.org") })
                })
            }
        }

        val domains = (WorkersRepository(api).customDomains("acc-1", "edge-api") as ApiResult.Success).value

        assertEquals("edge-api", service)
        assertEquals("api.example.org", domains.single().hostname)
    }

    @Test
    fun `secret values never appear in the toString of the request model`() {
        val value = SecretValue(name = "API_KEY", text = "very-secret")

        assertNull(Regex("very-secret").find(value.toString()))
    }

    @Test
    fun `binding toString does not expose plain-text values`() {
        val binding = WorkerBinding(type = "plain_text", name = "MODE", target = null, text = "prod-only")

        assertNull(Regex("prod-only").find(binding.toString()))
    }

    @Test
    fun `settings bindings keep their raw json for the mapper`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts/{scriptName}/settings") {
                DemoReply.ok(buildJsonObject {
                    put("compatibility_date", "2026-10-01")
                    put("bindings", buildJsonArray {
                        add(JsonObject(mapOf("type" to JsonPrimitive("kv_namespace"), "namespace_id" to JsonPrimitive("ns-1"))))
                    })
                })
            }
        }

        val settings = (WorkersRepository(api).settings("acc-1", "edge-api") as ApiResult.Success).value

        assertEquals("2026-10-01", settings.compatibilityDate)
        assertEquals("kv_namespace", settings.bindings.single()["type"]?.toString()?.trim('"'))
    }
}
