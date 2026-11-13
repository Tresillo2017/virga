package com.tomasps.virga.feature.r2.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2LifecycleRulesTest {

    @Test
    fun `saving a rule keeps the fields Virga does not edit`() {
        // Arrange
        val existing = parse(
            """{"id":"logs","enabled":true,"conditions":{"prefix":"logs/"},
               "abortMultipartUploadsTransition":{"condition":{"type":"Age","maxAge":86400}},
               "storageClassTransitions":[{"condition":{"type":"Age","maxAge":2592000},"storageClass":"InfrequentAccess"}]}""",
        )

        // Act
        val written = lifecycleRuleFrom("logs", enabled = false, prefix = "logs/", expireAfterDays = 30, existing = existing)

        // Assert
        assertTrue(written.containsKey("abortMultipartUploadsTransition"))
        assertTrue(written.containsKey("storageClassTransitions"))
        assertEquals(false, written["enabled"]!!.jsonPrimitive.content.toBoolean())
        val maxAge = written["deleteObjectsTransition"]!!.jsonObject["condition"]!!.jsonObject["maxAge"]!!
        assertEquals(30L * SECONDS_PER_DAY, maxAge.jsonPrimitive.long)
    }

    @Test
    fun `an unchanged expiry keeps the original transition exactly, even a date rule or an age that is not whole days`() {
        // Arrange
        val dateRule = parse("""{"id":"dated","enabled":true,"deleteObjectsTransition":{"condition":{"type":"Date","date":"2027-01-01"}}}""")
        val oddAge = parse("""{"id":"odd","enabled":true,"deleteObjectsTransition":{"condition":{"type":"Age","maxAge":90000}}}""")

        // Act
        val keptDate = lifecycleRuleFrom("dated", enabled = false, prefix = "x/", expireAfterDays = null, existing = dateRule, expiryChanged = false)
        val keptAge = lifecycleRuleFrom("odd", enabled = true, prefix = "", expireAfterDays = 1L, existing = oddAge, expiryChanged = false)

        // Assert
        assertEquals(dateRule["deleteObjectsTransition"], keptDate["deleteObjectsTransition"])
        assertEquals(oddAge["deleteObjectsTransition"], keptAge["deleteObjectsTransition"])
    }

    @Test
    fun `clearing the expiry field removes the transition`() {
        // Arrange
        val existing = parse("""{"id":"keep","enabled":true,"deleteObjectsTransition":{"condition":{"type":"Age","maxAge":86400}}}""")

        // Act
        val cleared = lifecycleRuleFrom("keep", enabled = true, prefix = "", expireAfterDays = null, existing = existing, expiryChanged = true)

        // Assert
        assertFalse(cleared.containsKey("deleteObjectsTransition"))
    }

    @Test
    fun `a rule without an expiry writes no delete transition`() {
        // Arrange
        val existing = parse("""{"id":"keep","enabled":true,"deleteObjectsTransition":{"condition":{"type":"Age","maxAge":86400}}}""")

        // Act
        val written = lifecycleRuleFrom("keep", enabled = true, prefix = "", expireAfterDays = null, existing = existing)

        // Assert
        assertFalse(written.containsKey("deleteObjectsTransition"))
    }

    @Test
    fun `expiry is read back in whole days and a date rule has no day count`() {
        // Arrange
        val ageRule = parse("""{"id":"a","conditions":{"prefix":"tmp/"},"deleteObjectsTransition":{"condition":{"type":"Age","maxAge":172800}}}""")
        val dateRule = parse("""{"id":"d","deleteObjectsTransition":{"condition":{"type":"Date","date":"2030-01-01T00:00:00Z"}}}""")

        // Act
        val age = ageRule.toLifecycleEntry()
        val date = dateRule.toLifecycleEntry()

        // Assert
        assertEquals(2L, age.expireAfterDays)
        assertEquals("tmp/", age.prefix)
        assertNull(date.expireAfterDays)
        assertEquals("d", date.id)
    }

    @Test
    fun `expiry days must be empty or a whole number from one up`() {
        assertFalse(isInvalidExpiryDays(""))
        assertFalse(isInvalidExpiryDays("30"))
        assertTrue(isInvalidExpiryDays("0"))
        assertTrue(isInvalidExpiryDays("soon"))
    }

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
