package com.tomasps.virga.core.ui.dialogs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedNameMatchTest {

    @Test
    fun `exact name matches`() {
        assertTrue(isTypedNameMatch("api-gateway", "api-gateway"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertTrue(isTypedNameMatch("  api-gateway \n", "api-gateway"))
    }

    @Test
    fun `different case does not match`() {
        assertFalse(isTypedNameMatch("API-Gateway", "api-gateway"))
    }

    @Test
    fun `partial name does not match`() {
        assertFalse(isTypedNameMatch("api-gate", "api-gateway"))
    }

    @Test
    fun `empty expected name never matches`() {
        assertFalse(isTypedNameMatch("", ""))
        assertFalse(isTypedNameMatch("anything", ""))
    }
}
