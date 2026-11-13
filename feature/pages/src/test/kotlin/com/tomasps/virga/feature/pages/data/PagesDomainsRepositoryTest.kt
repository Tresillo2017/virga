package com.tomasps.virga.feature.pages.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagesDomainsRepositoryTest {

    private val domainJson = buildJsonObject {
        put("id", "d-1")
        put("name", "docs.lantern.example")
        put("status", "pending")
        put("zone_tag", "zone-9")
        put("validation_data", buildJsonObject {
            put("method", "txt"); put("status", "pending")
            put("txt_name", "_cf-custom-hostname.docs"); put("txt_value", "token-123")
        })
    }

    @Test
    fun `adding a domain posts its name as JSON`() = runBlocking {
        var sentBody = ""
        val api = mockCloudflareApi {
            post("/accounts/{accountId}/pages/projects/{projectName}/domains") { request ->
                sentBody = request.bodyText.orEmpty(); DemoReply.ok(domainJson)
            }
        }

        val result = PagesDomainsRepository(api).add("acc-1", "lantern-docs", "docs.lantern.example")

        assertEquals("""{"name":"docs.lantern.example"}""", sentBody)
        assertEquals("token-123", (result as ApiResult.Success).value.validation?.txtValue)
    }

    @Test
    fun `retrying validation is a PATCH with no body to the domain path`() = runBlocking {
        var sentMethod = ""
        var sentBody: String? = "unset"
        val api = mockCloudflareApi {
            patch("/accounts/{accountId}/pages/projects/{projectName}/domains/{domainName}") { request ->
                sentMethod = request.method.value
                sentBody = request.bodyText
                DemoReply.ok(domainJson)
            }
        }

        PagesDomainsRepository(api).retryValidation("acc-1", "lantern-docs", "docs.lantern.example")

        assertEquals("PATCH", sentMethod)
        assertEquals(null, sentBody)
    }

    @Test
    fun `removing a domain deletes its escaped path`() = runBlocking {
        var seenDomain = ""
        val api = mockCloudflareApi {
            delete("/accounts/{accountId}/pages/projects/{projectName}/domains/{domainName}") { request ->
                seenDomain = request.pathParams["domainName"].orEmpty(); DemoReply.ok(JsonNull)
            }
        }

        val result = PagesDomainsRepository(api).remove("acc-1", "lantern-docs", "docs.lantern.example")

        assertEquals("docs.lantern.example", seenDomain)
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `domains are listed with their validation records`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}/domains") {
                DemoReply.ok(kotlinx.serialization.json.buildJsonArray { add(domainJson) })
            }
        }

        val result = PagesDomainsRepository(api).domains("acc-1", "lantern-docs")

        val domain = (result as ApiResult.Success).value.single()
        assertEquals("_cf-custom-hostname.docs", domain.validation?.txtName)
        assertEquals("zone-9", domain.zoneTag)
    }
}
