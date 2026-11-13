package com.tomasps.virga.feature.kv.value

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN_TTL = 60
private const val SHORT_TTL = 59

class KvBulkImportLimitsTest {

    @Test
    fun `a pair with a time to live under 60 seconds is refused locally`() {
        // Arrange
        val text = """[{"key":"a","value":"1","expiration_ttl":$SHORT_TTL}]"""

        // Act
        val parse = parseBulkImport(text)

        // Assert
        assertTrue(parse.pairs.isEmpty())
        assertEquals(PairProblem.TTL_TOO_SHORT, parse.rejected.single().problem)
    }

    @Test
    fun `a pair with a time to live of exactly 60 seconds is accepted`() {
        // Arrange
        val text = """[{"key":"a","value":"1","expiration_ttl":$MIN_TTL}]"""

        // Act
        val parse = parseBulkImport(text)

        // Assert
        assertEquals(1, parse.pairs.size)
        assertTrue(parse.rejected.isEmpty())
    }

    @Test
    fun `the size of a pair counts its metadata, not only the key and value`() {
        // Arrange
        val withoutMetadata = """[{"key":"a","value":"1"}]"""
        val withMetadata = """[{"key":"a","value":"1","metadata":{"owner":"a-long-owner-name-for-size"}}]"""

        // Act
        val plain = parseBulkImport(withoutMetadata).pairs.single().bytes
        val heavy = parseBulkImport(withMetadata).pairs.single().bytes

        // Assert
        assertTrue(heavy > plain)
    }
}
