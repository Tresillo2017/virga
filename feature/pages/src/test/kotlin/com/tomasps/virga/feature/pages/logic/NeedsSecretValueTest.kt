package com.tomasps.virga.feature.pages.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedsSecretValueTest {

    @Test
    fun `a new secret with no value needs one`() {
        assertTrue(EnvVarRules.needsSecretValue(EnvVarType.SECRET, value = "", existing = null))
    }

    @Test
    fun `a plain variable turned into a secret with no value needs one`() {
        val existing = EnvVarEntry("SITE", EnvVarType.PLAIN, "Lantern")

        assertTrue(EnvVarRules.needsSecretValue(EnvVarType.SECRET, value = "", existing = existing))
    }

    @Test
    fun `an existing secret kept as a secret may stay blank, which keeps the stored value`() {
        val existing = EnvVarEntry("TOKEN", EnvVarType.SECRET, value = null)

        assertFalse(EnvVarRules.needsSecretValue(EnvVarType.SECRET, value = "", existing = existing))
    }

    @Test
    fun `a plain variable with no value never needs one`() {
        assertFalse(EnvVarRules.needsSecretValue(EnvVarType.PLAIN, value = "", existing = null))
    }
}
