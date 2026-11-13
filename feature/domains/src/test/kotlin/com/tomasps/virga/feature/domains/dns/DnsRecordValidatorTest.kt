package com.tomasps.virga.feature.domains.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsRecordValidatorTest {

    private val free = DnsPlanLimits(isPaid = false)
    private val paid = DnsPlanLimits(isPaid = true)

    private fun form(type: String, name: String = "www", ttl: String = "300", vararg values: Pair<String, String>) =
        DnsRecordForm(type = type, name = name, ttl = ttl, values = values.toMap())

    private fun List<DnsFieldError>.reasons(): List<DnsErrorReason> = map { it.reason }

    @Test
    fun `A record accepts a dotted IPv4 address`() {
        // Arrange
        val f = form("A", values = arrayOf("content" to "192.0.2.10"))

        // Act
        val errors = validateDnsForm(f, free)

        // Assert
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `A record rejects an octet above 255`() {
        // Arrange
        val f = form("A", values = arrayOf("content" to "192.0.2.300"))

        // Act
        val errors = validateDnsForm(f, free)

        // Assert
        assertEquals(listOf(DnsErrorReason.INVALID_IPV4), errors.reasons())
    }

    @Test
    fun `AAAA record needs a colon and reasonable length`() {
        // Arrange
        val f = form("AAAA", values = arrayOf("content" to "192.0.2.1"))

        // Act
        val errors = validateDnsForm(f, free)

        // Assert
        assertEquals(listOf(DnsErrorReason.INVALID_IPV6), errors.reasons())
    }

    @Test
    fun `CNAME cannot point at its own name, ignoring case and the trailing dot`() {
        // Arrange
        val f = form("CNAME", name = "WWW.example.com", values = arrayOf("content" to "www.example.com."))

        // Act
        val errors = validateDnsForm(f, free)

        // Assert
        assertTrue(errors.reasons().contains(DnsErrorReason.SELF_CNAME))
    }

    @Test
    fun `TTL must be Auto or between 60 and 86400`() {
        // Arrange
        val tooLow = form("TXT", ttl = "30", values = arrayOf("content" to "v=1"))
        val auto = form("TXT", ttl = "1", values = arrayOf("content" to "v=1"))

        // Act
        val lowErrors = validateDnsForm(tooLow, free)
        val autoErrors = validateDnsForm(auto, free)

        // Assert
        assertEquals(listOf(DnsErrorReason.TTL_OUT_OF_RANGE), lowErrors.reasons())
        assertTrue(autoErrors.isEmpty())
    }

    @Test
    fun `proxy is refused for a type that cannot be proxied`() {
        // Arrange
        val f = DnsRecordForm(type = "MX", name = "example.com", proxied = true, values = mapOf("content" to "mail.example.com", "priority" to "10"))

        // Act
        val errors = validateDnsForm(f, free)

        // Assert
        assertTrue(errors.reasons().contains(DnsErrorReason.NOT_PROXIABLE))
    }

    @Test
    fun `comment is limited to 100 characters on Free and 500 on paid plans`() {
        // Arrange
        val long = "x".repeat(150)
        val f = DnsRecordForm(type = "TXT", name = "t", comment = long, values = mapOf("content" to "v=1"))

        // Act
        val freeErrors = validateDnsForm(f, free)
        val paidErrors = validateDnsForm(f, paid)

        // Assert
        assertEquals(listOf(DnsErrorReason.TOO_LONG), freeErrors.reasons())
        assertTrue(paidErrors.isEmpty())
    }

    @Test
    fun `tags are refused on Free and capped at 20 on paid plans`() {
        // Arrange
        val tags = (1..21).joinToString(",") { "k$it:v" }
        val f = DnsRecordForm(type = "TXT", name = "t", tags = tags, values = mapOf("content" to "v=1"))

        // Act
        val freeErrors = validateDnsForm(f, free)
        val paidErrors = validateDnsForm(f, paid)

        // Assert
        assertEquals(listOf(DnsErrorReason.TAGS_NOT_ALLOWED), freeErrors.reasons())
        assertEquals(listOf(DnsErrorReason.TOO_MANY_TAGS), paidErrors.reasons())
    }

    @Test
    fun `MX priority must fit in 16 bits`() {
        // Arrange
        val f = DnsRecordForm(type = "MX", name = "example.com", values = mapOf("content" to "mail.example.com", "priority" to "70000"))

        // Act
        val errors = validateDnsForm(f, free)

        // Assert
        assertEquals(listOf(DnsErrorReason.OUT_OF_RANGE), errors.reasons())
    }

    @Test
    fun `CAA tag must be issue, issuewild or iodef`() {
        // Arrange
        val f = DnsRecordForm(type = "CAA", name = "example.com", values = mapOf("flags" to "0", "tag" to "bogus", "value" to "ca.example"))

        // Act
        val errors = validateDnsForm(f, free)

        // Assert
        assertEquals(listOf(DnsErrorReason.INVALID_FORMAT), errors.reasons())
    }

    @Test
    fun `an unknown type only needs content`() {
        // Arrange
        val f = DnsRecordForm(type = "LOC", name = "x", values = mapOf("content" to "52 22 23.0 N"))

        // Act
        val errors = validateDnsForm(f, free)

        // Assert
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `a priority that is not a number says so instead of calling the field missing`() {
        // Arrange
        val f = form("MX", values = arrayOf("content" to "mail.example.com", "priority" to "x"))

        // Act
        val errors = validateDnsForm(f, free)

        // Assert
        assertEquals(listOf(DnsFieldError("priority", DnsErrorReason.NOT_A_NUMBER)), errors)
    }
}
