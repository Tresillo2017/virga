package com.tomasps.virga.feature.account.account

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDetailsFormatTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `account type is shown capitalised, not as the raw API value`() {
        assertEquals("Standard", friendlyAccountType("standard"))
        assertEquals("Enterprise", friendlyAccountType("enterprise"))
    }

    @Test
    fun `underscores in an account type become spaces`() {
        assertEquals("Self serve", friendlyAccountType("self_serve"))
    }

    @Test
    fun `missing or blank account type shows nothing`() {
        assertNull(friendlyAccountType(null))
        assertNull(friendlyAccountType("  "))
    }

    @Test
    fun `ISO creation time is shown as a date, never as the raw timestamp`() {
        val shown = friendlyCreatedOn("2024-05-01T09:00:00Z", utc)

        assertTrue(shown != null && shown.contains("2024"))
        assertFalse(shown!!.contains("T09"))
    }

    @Test
    fun `creation time that is not an ISO timestamp is hidden, not echoed`() {
        assertNull(friendlyCreatedOn("sometime in May", utc))
    }

    @Test
    fun `missing creation time shows nothing`() {
        assertNull(friendlyCreatedOn(null, utc))
        assertNull(friendlyCreatedOn("", utc))
    }
}
