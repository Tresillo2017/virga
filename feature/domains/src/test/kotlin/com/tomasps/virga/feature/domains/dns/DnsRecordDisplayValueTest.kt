package com.tomasps.virga.feature.domains.dns

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsRecordDisplayValueTest {

    @Test
    fun `SRV shows its fields in form order, not as JSON`() {
        // Arrange
        val record = DnsRecord(
            id = "srv-1",
            type = "SRV",
            data = JsonObject(
                mapOf(
                    F_TARGET to JsonPrimitive("sip.example.com"),
                    F_PORT to JsonPrimitive(5060),
                    F_PRIORITY to JsonPrimitive(10),
                    F_WEIGHT to JsonPrimitive(5),
                ),
            ),
        )

        // Act
        val value = record.displayValue()

        // Assert
        assertEquals("10 5 5060 sip.example.com", value)
    }

    @Test
    fun `CAA quotes its value`() {
        val record = DnsRecord(
            id = "caa-1",
            type = "CAA",
            data = JsonObject(mapOf(F_FLAGS to JsonPrimitive(0), F_TAG to JsonPrimitive("issue"), F_VALUE to JsonPrimitive("ca.example"))),
        )

        assertEquals("0 issue \"ca.example\"", record.displayValue())
    }

    @Test
    fun `plain content wins over data, and a null content with no data is empty`() {
        assertEquals("192.0.2.10", DnsRecord(id = "a", type = "A", content = "192.0.2.10").displayValue())
        assertEquals("", DnsRecord(id = "x", type = "SRV", content = null, data = JsonObject(emptyMap())).displayValue())
        assertEquals("", DnsRecord(id = "y", type = "A", content = null).displayValue())
    }
}
