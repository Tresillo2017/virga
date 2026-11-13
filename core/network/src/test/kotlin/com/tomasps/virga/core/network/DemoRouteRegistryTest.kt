package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRouteRegistryTest {
    @Serializable
    data class Named(val name: String)

    @Test
    fun `matches literal and parameterised segments and passes params through`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts/{scriptName}") { request ->
                DemoReply.ok(buildJsonObject { put("name", "${request.pathParams["accountId"]}:${request.pathParams["scriptName"]}") })
            }
        }
        val result = api.get<Named>("/accounts/acc%201/workers/scripts/hello-world") as ApiResult.Success
        assertEquals("acc 1:hello-world", result.value.name)
    }

    @Test
    fun `a catch-all segment matches keys containing slashes`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets/{bucket}/objects/{key...}") { request ->
                DemoReply.ok(buildJsonObject { put("name", request.pathParams["key"]!!) })
            }
        }
        val result = api.get<Named>(cloudflarePath("accounts", "a", "r2", "buckets", "b", "objects", "photos/2026/cat one.jpg")) as ApiResult.Success
        assertEquals("photos/2026/cat one.jpg", result.value.name)
    }

    @Test
    fun `query parameters reach the handler`() = runBlocking {
        val api = mockCloudflareApi {
            get("/zones") { request -> DemoReply.ok(buildJsonObject { put("name", request.query["page"] ?: "none") }) }
        }
        val result = api.get<Named>("/zones", mapOf("page" to "3")) as ApiResult.Success
        assertEquals("3", result.value.name)
    }

    @Test
    fun `an unmatched route returns a Cloudflare style not found`() = runBlocking {
        val api = mockCloudflareApi { get("/zones") { DemoReply.ok(buildJsonObject { put("name", "x") }) } }
        val error = (api.get<Named>("/nope") as ApiResult.Failure).error
        assertTrue(error is ApiError.NotFound)
        assertEquals(listOf(7003), error.codes)
    }

    @Test
    fun `the method must match`() = runBlocking {
        val api = mockCloudflareApi { get("/zones") { DemoReply.ok(buildJsonObject { put("name", "x") }) } }
        val result = api.delete<Named>("/zones")
        assertTrue(result is ApiResult.Failure)
    }
}
