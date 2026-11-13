package com.tomasps.virga.feature.kv.value

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_800_000_000L

class KvValidationTest {

    @Test
    fun `dot and dot-dot key names are refused with their own problem`() {
        assertEquals(KeyNameProblem.DOT_SEGMENT, validateKeyName("."))
        assertEquals(KeyNameProblem.DOT_SEGMENT, validateKeyName(".."))
    }

    @Test
    fun `empty key name is refused`() {
        assertEquals(KeyNameProblem.EMPTY, validateKeyName(""))
    }

    @Test
    fun `key names of exactly 512 bytes pass and 513 bytes fail`() {
        assertNull(validateKeyName("a".repeat(512)))
        assertEquals(KeyNameProblem.TOO_LONG, validateKeyName("a".repeat(513)))
    }

    @Test
    fun `key length counts utf-8 bytes, not characters`() {
        // Each euro sign is three bytes, so 171 of them are 513 bytes
        assertEquals(KeyNameProblem.TOO_LONG, validateKeyName("€".repeat(171)))
    }

    @Test
    fun `slashes and odd characters are valid key names`() {
        assertNull(validateKeyName("images/2026/a b%?#.png"))
    }

    @Test
    fun `blank metadata means no metadata`() {
        val result = validateMetadataText("   ") as MetadataCheck.Valid
        assertNull(result.element)
    }

    @Test
    fun `metadata must be json`() {
        assertEquals(MetadataProblem.INVALID_JSON, (validateMetadataText("{oops") as MetadataCheck.Invalid).problem)
    }

    @Test
    fun `json null metadata is treated as none`() {
        assertNull((validateMetadataText("null") as MetadataCheck.Valid).element.takeIf { it != JsonNull })
    }

    @Test
    fun `metadata over 1024 compact utf-8 bytes is refused`() {
        // Arrange
        val big = """{"x":"${"a".repeat(1100)}"}"""

        // Act
        val result = validateMetadataText(big)

        // Assert
        assertEquals(MetadataProblem.TOO_LARGE, (result as MetadataCheck.Invalid).problem)
    }

    @Test
    fun `metadata of exactly 1024 bytes passes`() {
        // Arrange: {"x":"..."} is 8 bytes of framing around the padding
        val padding = "a".repeat(1024 - 8)
        val text = """{"x":"$padding"}"""

        // Act
        val result = validateMetadataText(text)

        // Assert
        assertTrue(result is MetadataCheck.Valid)
        assertTrue(result.let { (it as MetadataCheck.Valid).element is JsonObject })
    }

    @Test
    fun `value over 25 MiB is refused`() {
        assertNull(validateValueSize(KV_MAX_VALUE_BYTES))
        assertEquals(ValueProblem.TOO_LARGE, validateValueSize(KV_MAX_VALUE_BYTES + 1))
    }

    @Test
    fun `ttl under 60 seconds is refused and 60 passes`() {
        assertEquals(KvExpirationProblem.TTL_TOO_SHORT, validateExpiration(KvExpiration.Ttl(59), NOW))
        assertNull(validateExpiration(KvExpiration.Ttl(60), NOW))
    }

    @Test
    fun `absolute expiration less than 60 seconds away is refused`() {
        assertEquals(KvExpirationProblem.ABSOLUTE_TOO_SOON, validateExpiration(KvExpiration.Absolute(NOW + 59), NOW))
        assertNull(validateExpiration(KvExpiration.Absolute(NOW + 60), NOW))
    }

    @Test
    fun `no expiration is always valid`() {
        assertNull(validateExpiration(KvExpiration.None, NOW))
    }
}
