package com.tomasps.virga.feature.domains.zones

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurgeRequestTest {

    @Test
    fun `purge everything sends only purge_everything true`() {
        // Arrange
        val request = PurgeRequest.Everything

        // Act
        val json = request.toJson()

        // Assert
        assertEquals(setOf("purge_everything"), json.keys)
        assertEquals("true", json["purge_everything"]!!.jsonPrimitive.content)
    }

    @Test
    fun `file purge refuses URLs without a scheme`() {
        // Arrange
        val request = PurgeRequest.Files(listOf("example.com/a.css"))

        // Act
        val problem = validatePurge(request)

        // Assert
        assertEquals(PurgeProblem.NEEDS_SCHEME, problem)
    }

    @Test
    fun `host purge refuses a scheme`() {
        // Arrange
        val request = PurgeRequest.Hosts(listOf("https://example.com"))

        // Act
        val problem = validatePurge(request)

        // Assert
        assertEquals(PurgeProblem.MUST_NOT_HAVE_SCHEME, problem)
    }

    @Test
    fun `empty selection and oversize selection are both refused`() {
        // Arrange
        val empty = PurgeRequest.Tags(emptyList())
        val tooMany = PurgeRequest.Tags((1..(MAX_PURGE_ITEMS + 1)).map { "t$it" })

        // Act / Assert
        assertEquals(PurgeProblem.EMPTY, validatePurge(empty))
        assertEquals(PurgeProblem.TOO_MANY, validatePurge(tooMany))
    }

    @Test
    fun `a valid tag selection passes and serialises as an array`() {
        // Arrange
        val request = PurgeRequest.Tags(listOf("product-images", "blog"))

        // Act
        val problem = validatePurge(request)
        val json = request.toJson()

        // Assert
        assertNull(problem)
        assertEquals(2, json["tags"]!!.jsonArray.size)
    }

    @Test
    fun `single-file purge accepts up to the documented 100 URLs and refuses 101`() {
        // Arrange
        val atLimit = PurgeRequest.Files((1..MAX_PURGE_ITEMS).map { "https://example.com/$it.css" })
        val overLimit = PurgeRequest.Files((1..MAX_PURGE_ITEMS + 1).map { "https://example.com/$it.css" })

        // Act and assert
        assertEquals(null, validatePurge(atLimit))
        assertEquals(PurgeProblem.TOO_MANY, validatePurge(overLimit))
        assertEquals(100, MAX_PURGE_FILES)
    }

    @Test
    fun `pasted text splits on lines and commas and drops blanks`() {
        // Arrange
        val text = "example.com/a\n\n example.com/b , ,example.com/c"

        // Act
        val items = parsePurgeItems(text)

        // Assert
        assertEquals(listOf("example.com/a", "example.com/b", "example.com/c"), items)
        assertTrue(items.isNotEmpty())
    }
}
