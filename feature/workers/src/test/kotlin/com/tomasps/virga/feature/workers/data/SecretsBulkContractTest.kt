package com.tomasps.virga.feature.workers.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the bulk secrets request to the reference: PATCH .../secrets-bulk, `secrets` map, null deletes. */
class SecretsBulkContractTest {

    @Test
    fun `bulk update patches the secrets-bulk path with a secret_text object for saves and null for deletes`() = runBlocking {
        var seenMethod: String? = null
        var seenPath: String? = null
        var seenBody: JsonObject? = null
        val api = mockCloudflareApi {
            patch("/accounts/{accountId}/workers/scripts/{scriptName}/secrets-bulk") { request ->
                seenMethod = request.method.value
                seenPath = request.path
                seenBody = Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject
                DemoReply.ok(buildJsonObject { })
            }
        }

        val result = WorkersRepository(api).updateSecrets(
            accountId = "acc-1",
            scriptName = "edge-api",
            changes = mapOf("API_KEY" to "value-1", "OLD_SECRET" to null),
        )

        assertTrue(result is ApiResult.Success)
        assertEquals("PATCH", seenMethod)
        assertTrue(seenPath.orEmpty().endsWith("/workers/scripts/edge-api/secrets-bulk"))
        val secrets = seenBody!!["secrets"]!!.jsonObject
        val saved = secrets["API_KEY"]!!.jsonObject
        assertEquals("secret_text", saved["type"]!!.jsonPrimitive.content)
        assertEquals("API_KEY", saved["name"]!!.jsonPrimitive.content)
        assertEquals("value-1", saved["text"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, secrets["OLD_SECRET"])
    }
}
