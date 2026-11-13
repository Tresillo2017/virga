package com.tomasps.virga.feature.domains.dns

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsRecordCodecTest {

    @Test
    fun `proxied A record sends proxied true and Auto TTL even when a custom TTL was typed`() {
        // Arrange
        val form = DnsRecordForm(type = "A", name = "www", ttl = "300", proxied = true, values = mapOf("content" to "192.0.2.1"))

        // Act
        val body = form.toRequestBody(isPaidPlan = false)

        // Assert
        assertTrue(body["proxied"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(AUTO_TTL, body["ttl"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `TXT record never sends proxied, even if the flag was left on`() {
        // Arrange
        val form = DnsRecordForm(type = "TXT", name = "_t", proxied = true, values = mapOf("content" to "v=1"))

        // Act
        val body = form.toRequestBody(isPaidPlan = false)

        // Assert
        assertFalse(body.containsKey("proxied"))
    }

    @Test
    fun `MX record sends priority as a top-level number`() {
        // Arrange
        val form = DnsRecordForm(type = "MX", name = "example.com", values = mapOf("content" to "mail.example.com", "priority" to "10"))

        // Act
        val body = form.toRequestBody(isPaidPlan = false)

        // Assert
        assertEquals(10, body["priority"]!!.jsonPrimitive.content.toInt())
        assertEquals("mail.example.com", body["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `SRV record sends its fields inside a data object, not content`() {
        // Arrange
        val values = mapOf("priority" to "10", "weight" to "5", "port" to "5060", "target" to "sip.example.com")
        val form = DnsRecordForm(type = "SRV", name = "_sip._tcp", values = values)

        // Act
        val body = form.toRequestBody(isPaidPlan = false)

        // Assert
        val data = body["data"]!!.jsonObject
        assertEquals(5060, data["port"]!!.jsonPrimitive.content.toInt())
        assertEquals("sip.example.com", data["target"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("content"))
    }

    @Test
    fun `tags are sent only on paid plans`() {
        // Arrange
        val form = DnsRecordForm(type = "A", name = "x", tags = "owner:mail, team:web", values = mapOf("content" to "192.0.2.1"))

        // Act
        val free = form.toRequestBody(isPaidPlan = false)
        val paid = form.toRequestBody(isPaidPlan = true)

        // Assert
        assertFalse(free.containsKey("tags"))
        assertEquals(2, paid["tags"]!!.jsonArray.size)
    }

    @Test
    fun `record read from the API opens into a form with its structured values`() {
        // Arrange
        val record = DnsRecord(
            id = "rec-1",
            name = "_sip._tcp.example.com",
            type = "SRV",
            ttl = 3600,
            data = buildJsonObject { put("priority", 10); put("weight", 5); put("port", 5060); put("target", "sip.example.com") },
        )

        // Act
        val form = record.toForm()

        // Assert
        assertEquals("5060", form.values["port"])
        assertEquals("sip.example.com", form.values["target"])
    }

    @Test
    fun `unknown record type keeps its content and cannot be proxied`() {
        // Arrange
        val record = DnsRecord(id = "rec-2", name = "x.example.com", type = "LOC", content = "52 22 23.0 N", proxied = true)

        // Act
        val form = record.toForm()

        // Assert
        assertEquals("52 22 23.0 N", form.values["content"])
        assertNull(form.recordType)
        assertFalse(form.toRequestBody(isPaidPlan = false).containsKey("proxied"))
    }

    @Test
    fun `parseTags drops blanks and trims`() {
        // Arrange
        val raw = " owner:mail ,, team:web ,"

        // Act
        val tags = parseTags(raw)

        // Assert
        assertEquals(listOf("owner:mail", "team:web"), tags)
    }

    @Test
    fun `changing the type clears value fields and proxy when the new type is not proxiable`() {
        // Arrange
        val form = DnsRecordForm(type = "A", proxied = true, values = mapOf("content" to "192.0.2.1"))

        // Act
        val next = form.withType(DnsRecordType.MX)

        // Assert
        assertTrue(next.values.isEmpty())
        assertFalse(next.proxied)
        assertEquals("MX", next.type)
    }
}
