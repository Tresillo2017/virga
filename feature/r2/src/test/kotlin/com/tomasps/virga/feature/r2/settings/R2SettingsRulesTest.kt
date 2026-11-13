package com.tomasps.virga.feature.r2.settings

import com.tomasps.virga.feature.r2.api.R2LockCondition
import com.tomasps.virga.feature.r2.api.R2LockRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2SettingsRulesTest {

    @Test
    fun `origins split on commas and new lines and keep wildcards`() {
        // Act
        val origins = parseOrigins("https://a.example.com, https://b.example.com\n*")

        // Assert
        assertEquals(listOf("https://a.example.com", "https://b.example.com", "*"), origins)
    }

    @Test
    fun `an origin without a scheme or wildcard is reported as invalid`() {
        // Act
        val invalid = invalidOrigins(listOf("https://ok.example.com", "example.com"))

        // Assert
        assertEquals(listOf("example.com"), invalid)
    }

    @Test
    fun `a lock rule with a day count becomes an Age condition in seconds`() {
        // Act
        val rule = lockRuleFrom(id = "logs", prefix = "logs/", indefinite = false, days = "7")

        // Assert
        assertEquals(R2LockCondition(type = "Age", maxAgeSeconds = 7L * 86_400L), rule.condition)
        assertEquals("logs/", rule.prefix)
    }

    @Test
    fun `an indefinite lock rule has no day count`() {
        // Act
        val rule = lockRuleFrom(id = "all", prefix = "", indefinite = true, days = "")

        // Assert
        assertEquals("Indefinite", rule.condition.type)
        assertNull(rule.prefix)
    }

    @Test
    fun `a lock rule id must be new and non blank`() {
        // Arrange
        val existing = listOf(R2LockRule(id = "logs"))

        // Assert
        assertTrue(lockIdError("", existing) != null)
        assertTrue(lockIdError("logs", existing) != null)
        assertNull(lockIdError("images", existing))
    }

    @Test
    fun `day count must be a positive whole number when the rule is not indefinite`() {
        assertTrue(lockDaysError(indefinite = false, days = "0") != null)
        assertTrue(lockDaysError(indefinite = false, days = "abc") != null)
        assertNull(lockDaysError(indefinite = false, days = "30"))
        assertNull(lockDaysError(indefinite = true, days = ""))
    }

    @Test
    fun `custom domain names need a dot and a zone id`() {
        assertTrue(domainError(domain = "localhost", zoneId = "z") != null)
        assertTrue(domainError(domain = "assets.example.com", zoneId = "") != null)
        assertNull(domainError(domain = "assets.example.com", zoneId = "zone-1"))
    }
}
