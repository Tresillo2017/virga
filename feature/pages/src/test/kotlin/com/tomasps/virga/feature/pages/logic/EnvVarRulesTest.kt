package com.tomasps.virga.feature.pages.logic

import com.tomasps.virga.feature.pages.data.PagesDeploymentConfigValues
import com.tomasps.virga.feature.pages.data.PagesEnvVar
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvVarRulesTest {

    private fun envVars(vararg pairs: Pair<String, PagesEnvVar?>) =
        PagesDeploymentConfigValues(envVars = mapOf(*pairs))

    @Test
    fun `secret values are never carried into the entry, and entries are sorted by name`() {
        // Arrange
        val config = envVars(
            "ZETA" to PagesEnvVar(type = "plain_text", value = "1"),
            "API_KEY" to PagesEnvVar(type = "secret_text", value = ""),
        )

        // Act
        val entries = EnvVarRules.entriesOf(config)

        // Assert
        assertEquals(listOf("API_KEY", "ZETA"), entries.map { it.name })
        assertEquals(EnvVarType.SECRET, entries[0].type)
        assertNull(entries[0].value)
        assertEquals("1", entries[1].value)
    }

    @Test
    fun `an unknown or null env var type is treated as plain text`() {
        val entries = EnvVarRules.entriesOf(envVars("A" to null, "B" to PagesEnvVar(type = "future_type", value = "x")))

        assertEquals(listOf(EnvVarType.PLAIN, EnvVarType.PLAIN), entries.map { it.type })
    }

    @Test
    fun `an existing secret saved without a new value sends nothing`() {
        val existing = EnvVarEntry(name = "API_KEY", type = EnvVarType.SECRET, value = null)

        val body = EnvVarRules.saveBody("production", "API_KEY", EnvVarType.SECRET, "", existing)

        assertNull(body)
    }

    @Test
    fun `saving a plain value patches only that key under the environment`() {
        val body = EnvVarRules.saveBody("preview", "MODE", EnvVarType.PLAIN, "staging", null)!!

        val envVars = body.jsonObject["deployment_configs"]!!.jsonObject["preview"]!!.jsonObject["env_vars"]!!.jsonObject
        assertEquals(setOf("MODE"), envVars.keys)
        assertEquals("plain_text", envVars["MODE"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("staging", envVars["MODE"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `deleting a variable sends an explicit null for its key`() {
        val body = EnvVarRules.deleteBody("production", "OLD")

        val envVars = body["deployment_configs"]!!.jsonObject["production"]!!.jsonObject["env_vars"]!!.jsonObject
        assertEquals(JsonNull, envVars["OLD"])
    }

    @Test
    fun `names must start with a letter or underscore and contain only word characters`() {
        assertTrue(EnvVarRules.isValidName("API_KEY_2"))
        assertFalse(EnvVarRules.isValidName("2FAST"))
        assertFalse(EnvVarRules.isValidName("bad name"))
        assertFalse(EnvVarRules.isValidName(""))
    }

    @Test
    fun `entry toString never prints the value`() {
        val entry = EnvVarEntry(name = "TOKEN", type = EnvVarType.PLAIN, value = "hunter2")

        assertFalse(entry.toString().contains("hunter2"))
    }
}
