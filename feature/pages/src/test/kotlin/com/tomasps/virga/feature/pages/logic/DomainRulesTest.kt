package com.tomasps.virga.feature.pages.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainRulesTest {

    @Test
    fun `accepts fully qualified hostnames and normalises case and a trailing dot`() {
        assertTrue(DomainRules.isValidHostname("Docs.Lantern.example"))
        assertTrue(DomainRules.isValidHostname("docs.lantern.example."))
        assertEquals("docs.lantern.example", DomainRules.normalize("  Docs.Lantern.example. "))
    }

    @Test
    fun `rejects single labels, empty input, URLs and paths`() {
        assertFalse(DomainRules.isValidHostname("localhost"))
        assertFalse(DomainRules.isValidHostname(""))
        assertFalse(DomainRules.isValidHostname("https://docs.lantern.example"))
        assertFalse(DomainRules.isValidHostname("docs.lantern.example/path"))
    }
}
