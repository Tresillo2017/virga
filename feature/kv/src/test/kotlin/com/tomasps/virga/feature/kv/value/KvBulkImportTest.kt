package com.tomasps.virga.feature.kv.value

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KvBulkImportTest {

    @Test
    fun `valid pairs are kept and bad pairs are reported by key`() {
        // Arrange
        val text = """
            [
              {"key":"good","value":"1"},
              {"key":"..","value":"x"},
              {"key":"","value":"x"},
              {"value":"no key"},
              {"key":"novalue"}
            ]
        """.trimIndent()

        // Act
        val parsed = parseBulkImport(text)

        // Assert
        assertEquals(1, parsed.pairs.size)
        assertEquals(listOf("good"), parsed.pairs.map { it.key })
        assertEquals(4, parsed.rejected.size)
        assertTrue(parsed.rejected.any { it.key == ".." })
    }

    @Test
    fun `a file that is not a json array is refused as a whole`() {
        assertEquals(BulkImportProblem.NOT_AN_ARRAY, parseBulkImport("{}").problem)
        assertEquals(BulkImportProblem.NOT_JSON, parseBulkImport("[oops").problem)
    }

    @Test
    fun `more than 10,000 pairs is refused`() {
        // Arrange
        val pairs = (1..(KV_BULK_MAX_PAIRS + 1)).joinToString(",") { """{"key":"k$it","value":"v"}""" }

        // Act
        val parsed = parseBulkImport("[$pairs]")

        // Assert
        assertEquals(BulkImportProblem.TOO_MANY_PAIRS, parsed.problem)
    }

    @Test
    fun `chunks never exceed the pair limit`() {
        // Arrange
        val pairs = (1..25).map { ParsedPair(key = "k$it", element = JsonObject(emptyMap()), bytes = 20) }

        // Act
        val chunks = chunkBulkPairs(pairs, maxPairs = 10, maxBytes = 1_000_000)

        // Assert
        assertEquals(listOf(10, 10, 5), chunks.map { it.size })
    }

    @Test
    fun `chunks never exceed the byte limit`() {
        // Arrange
        val pairs = (1..4).map { ParsedPair(key = "k$it", element = JsonObject(emptyMap()), bytes = 40) }

        // Act
        val chunks = chunkBulkPairs(pairs, maxPairs = 100, maxBytes = 100)

        // Assert
        assertEquals(listOf(2, 2), chunks.map { it.size })
    }

    @Test
    fun `the brackets and the comma between pairs count toward the byte limit`() {
        // Arrange: two 4-byte pairs sent as [a,b] take 2 + 4 + 4 + 1 = 11 bytes, which is over a 10-byte limit
        val pairs = (1..2).map { ParsedPair(key = "k$it", element = JsonObject(emptyMap()), bytes = 4) }

        // Act
        val chunks = chunkBulkPairs(pairs, maxPairs = 100, maxBytes = 10)

        // Assert
        assertEquals(listOf(1, 1), chunks.map { it.size })
    }

    @Test
    fun `every chunk, sent as a json array, fits the byte limit`() {
        // Arrange
        val maxBytes = 300L
        val text = (1..60).joinToString(prefix = "[", postfix = "]") { i ->
            """{"key":"key-$i","value":"${"v".repeat(i % 17)}"}"""
        }
        val pairs = parseBulkImport(text).pairs

        // Act
        val chunks = chunkBulkPairs(pairs, maxPairs = 100, maxBytes = maxBytes)

        // Assert
        chunks.forEach { chunk ->
            val sent = JsonArray(chunk.map { it.element }).toString().encodeToByteArray().size
            assertTrue("chunk of ${chunk.size} sent $sent bytes", sent <= maxBytes)
        }
        assertEquals(pairs.size, chunks.sumOf { it.size })
    }
}
