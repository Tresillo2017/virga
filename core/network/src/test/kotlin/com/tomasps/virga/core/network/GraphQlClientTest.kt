package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphQlClientTest {
    @Serializable
    data class Viewer(val accountTag: String)

    @Serializable
    data class Data(val viewer: Viewer)

    @Test
    fun `sends the query and variables and decodes data`() = runBlocking {
        var sentBody = ""
        val api = mockCloudflareApi {
            post("/graphql") { request ->
                sentBody = request.bodyText.orEmpty()
                DemoReply.graphQl(buildJsonObject { put("viewer", buildJsonObject { put("accountTag", "acc1") }) })
            }
        }
        val result = api.graphQl.query(
            query = "query(\$tag: String) { viewer { accountTag } }",
            variables = buildJsonObject { put("tag", "acc1") },
            deserializer = Data.serializer(),
        )
        assertEquals("acc1", (result as ApiResult.Success).value.viewer.accountTag)
        val sent = kotlinx.serialization.json.Json.parseToJsonElement(sentBody).jsonObject
        assertEquals("acc1", sent["variables"]!!.jsonObject["tag"]!!.jsonPrimitive.content)
    }

    @Test
    fun `graphql errors are surfaced even when the HTTP status is 200`() = runBlocking {
        val api = mockCloudflareApi {
            post("/graphql") { DemoReply.graphQlErrors("unknown field 'foo'") }
        }
        val error = (api.graphQl.query("{ foo }", JsonObject(emptyMap()), Data.serializer()) as ApiResult.Failure).error
        assertTrue(error is ApiError.GraphQl)
        assertEquals("unknown field 'foo'", error.messages.single().message)
    }

    @Test
    fun `a missing data field is a decode error`() = runBlocking {
        val api = mockCloudflareApi { post("/graphql") { DemoReply.graphQl(null) } }
        val error = (api.graphQl.query("{ x }", JsonObject(emptyMap()), Data.serializer()) as ApiResult.Failure).error
        assertTrue(error is ApiError.Decode)
    }

    @Test
    fun `an HTTP failure maps through the status table`() = runBlocking {
        val api = mockCloudflareApi { post("/graphql") { DemoReply.failure(HttpStatusCode.Unauthorized, 10000, "no") } }
        val error = (api.graphQl.query("{ x }", JsonObject(emptyMap()), Data.serializer()) as ApiResult.Failure).error
        assertTrue(error is ApiError.Unauthorized)
    }
}
