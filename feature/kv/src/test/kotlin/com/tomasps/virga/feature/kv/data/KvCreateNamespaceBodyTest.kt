package com.tomasps.virga.feature.kv.data

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val NS_BASE = "/accounts/{accountId}/storage/kv/namespaces"

class KvCreateNamespaceBodyTest {

    @Test
    fun `create sends only the title when no jurisdiction is chosen, and adds it when one is`() = runBlocking {
        // Arrange
        val bodies = mutableListOf<String>()
        val api = mockCloudflareApi {
            post(NS_BASE) { request ->
                bodies += request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("id", "ns-1"); put("title", "Scratch") })
            }
        }
        val repository = KvRepository(api)

        // Act
        repository.createNamespace(ACCOUNT, "Scratch")
        repository.createNamespace(ACCOUNT, "Scratch", KvJurisdiction.EU)

        // Assert
        assertEquals("""{"title":"Scratch"}""", bodies[0])
        assertEquals("""{"title":"Scratch","jurisdiction":"eu"}""", bodies[1])
    }
}
