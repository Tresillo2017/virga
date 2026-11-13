package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2ListEnvelopeParserTest {

    private val parser = R2ListEnvelopeParser(CloudflareJson)

    @Test
    fun `object page keeps keys, folders and the next cursor when truncated`() {
        // Arrange
        val body = """
            {"success":true,"errors":[],"messages":[],
             "result":[{"key":"photos/a.png","size":12,"etag":"abc","last_modified":"2026-01-02T03:04:05Z",
                        "custom_metadata":{"owner":"team"},"http_metadata":{"contentType":"image/png"},
                        "storage_class":"Standard","ssec":false}],
             "result_info":{"cursor":"next-1","delimited":["photos/2025/"],"is_truncated":true,"per_page":1}}
        """.trimIndent()

        // Act
        val result = parser.parseObjectPage(body)

        // Assert
        val page = (result as ApiResult.Success).value
        assertEquals(listOf("photos/a.png"), page.objects.map { it.key })
        assertEquals(12L, page.objects.single().size)
        assertEquals("image/png", page.objects.single().contentType)
        assertEquals(mapOf("owner" to "team"), page.objects.single().customMetadata)
        assertEquals(listOf("photos/2025/"), page.folders)
        assertEquals("next-1", page.nextCursor)
    }

    @Test
    fun `last page has no next cursor even when a cursor string is present`() {
        // Arrange
        val body = """{"success":true,"result":[],"result_info":{"cursor":"stale","is_truncated":false}}"""

        // Act
        val page = (parser.parseObjectPage(body) as ApiResult.Success).value

        // Assert
        assertNull(page.nextCursor)
    }

    @Test
    fun `missing result and unknown fields decode to an empty page`() {
        // Arrange
        val body = """{"success":true,"result_info":{"is_truncated":false},"new_field":{"x":1}}"""

        // Act
        val page = (parser.parseObjectPage(body) as ApiResult.Success).value

        // Assert
        assertTrue(page.objects.isEmpty())
        assertTrue(page.folders.isEmpty())
    }

    @Test
    fun `items without a key are skipped instead of failing the whole page`() {
        // Arrange
        val body = """{"success":true,"result":[{"size":1},{"key":"ok.txt","size":2}]}"""

        // Act
        val page = (parser.parseObjectPage(body) as ApiResult.Success).value

        // Assert
        assertEquals(listOf("ok.txt"), page.objects.map { it.key })
    }

    @Test
    fun `a body that is not an envelope is a decode failure`() {
        // Act
        val result = parser.parseObjectPage("<html>gateway</html>")

        // Assert
        assertTrue((result as ApiResult.Failure).error is ApiError.Decode)
    }

    @Test
    fun `bucket page reads names and the cursor`() {
        // Arrange
        val body = """
            {"success":true,"result":{"buckets":[{"name":"alpha","creation_date":"2026-01-01T00:00:00Z","location":"enam","storage_class":"Standard"}]},
             "result_info":{"cursor":"alpha","per_page":50}}
        """.trimIndent()

        // Act
        val page = (parser.parseBucketPage(body) as ApiResult.Success).value

        // Assert
        assertEquals(listOf("alpha"), page.buckets.map { it.name })
        assertEquals("enam", page.buckets.single().location)
        assertEquals("alpha", page.nextCursor)
    }
}
