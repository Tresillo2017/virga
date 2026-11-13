package com.tomasps.virga.feature.domains.demo

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class BindZoneParserTest {

    @Test
    fun `reads name, ttl, class, type and content, and skips comments and blank lines`() {
        // Arrange
        val text = "; header\n\nwww 600 IN A 192.0.2.10 ; trailing comment\n"

        // Act
        val records = parseBindZone(text)

        // Assert
        assertEquals(listOf(BindRecord("www", "A", "192.0.2.10", ttl = 600, priority = null)), records)
    }

    @Test
    fun `an MX line keeps its priority apart from the content`() {
        // Arrange / Act
        val record = parseBindZone("@ IN MX 20 mail.example.com.").single()

        // Assert
        assertEquals(20, record.priority)
        assertEquals("mail.example.com.", record.content)
    }

    @Test
    fun `a line without a type is skipped instead of failing the import`() {
        // Arrange / Act
        val records = parseBindZone("broken\nok IN A 192.0.2.4")

        // Assert
        assertEquals(listOf("ok"), records.map { it.name })
    }

    @Test
    fun `a record with no ttl uses the automatic value`() {
        // Arrange / Act
        val record = parseBindZone("api IN TXT \"v=spf1 -all\"").single()

        // Assert
        assertEquals(1, record.ttl)
        assertEquals("\"v=spf1 -all\"", record.content)
    }

    @Test
    fun `the origin directive qualifies relative names and leaves absolute ones alone`() {
        // Arrange
        val text = "\$ORIGIN example.net.\nwww 300 IN A 192.0.2.10\nmail.other.org. 300 IN A 192.0.2.11\n@ 300 IN TXT \"x\"\n"

        // Act
        val names = parseBindZone(text).map { it.name }

        // Assert
        assertEquals(listOf("www.example.net", "mail.other.org.", "example.net"), names)
    }

    @Test
    fun `the TTL directive sets the TTL of records that give none`() {
        // Arrange
        val text = "\$TTL 900\nwww IN A 192.0.2.10\nmail 60 IN A 192.0.2.11\n"

        // Act
        val ttls = parseBindZone(text).map { it.ttl }

        // Assert
        assertEquals(listOf(900, 60), ttls)
    }

    @Test
    fun `an indented line repeats the owner of the line above`() {
        // Arrange
        val text = "www 300 IN A 192.0.2.10\n        IN A 192.0.2.11\n"

        // Act
        val records = parseBindZone(text)

        // Assert
        assertEquals(listOf("www", "www"), records.map { it.name })
        assertEquals(listOf("192.0.2.10", "192.0.2.11"), records.map { it.content })
    }

    @Test
    fun `a record whose parentheses span several lines is read as one line, and an SOA is skipped`() {
        // Arrange
        val text = """
            @ IN SOA ns1.example.net. hostmaster.example.net. (
                    2026110101 ; serial
                    7200       ; refresh
                    300 )      ; minimum
            www IN A 192.0.2.10
        """.trimIndent()

        // Act
        val records = parseBindZone(text)

        // Assert
        assertEquals(listOf(BindRecord("www", "A", "192.0.2.10", ttl = 1, priority = null)), records)
    }

    @Test
    fun `a semicolon inside quotes is part of the value, not a comment`() {
        // Arrange / Act
        val record = parseBindZone("@ IN TXT \"a;b c\"").single()

        // Assert
        assertEquals("\"a;b c\"", record.content)
    }

    @Test
    fun `the demo keeps a TXT value without quotes and a host name without its trailing dot`() {
        // Arrange
        val txt = BindRecord("@", "TXT", "\"v=spf1 -all\"", ttl = 1, priority = null)
        val mx = BindRecord("@", "MX", "mail.example.com.", ttl = 1, priority = 10)

        // Act / Assert
        assertEquals("v=spf1 -all", txt.toDemoJson()["content"]?.jsonPrimitive?.content)
        assertEquals("mail.example.com", mx.toDemoJson()["content"]?.jsonPrimitive?.content)
    }
}
