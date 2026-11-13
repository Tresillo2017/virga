package com.tomasps.virga.feature.workers.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostnameTest {

    @Test
    fun `a normal hostname is valid`() {
        assertTrue(isValidHostname("api.example.com"))
    }

    @Test
    fun `case, surrounding space and a trailing dot are normalised before the check`() {
        assertEquals("shop.example.org", normaliseHostname("  Shop.Example.org. "))
        assertTrue(isValidHostname("  Shop.Example.org. "))
    }

    @Test
    fun `text with spaces, a bare label or a bad tld is rejected`() {
        assertFalse(isValidHostname("not a hostname"))
        assertFalse(isValidHostname("localhost"))
        assertFalse(isValidHostname("example.c"))
        assertFalse(isValidHostname("-bad.example.com"))
    }
}

class WatchRationaleTest {

    @Test
    fun `the rationale comes first on Android 13 and later when notifications are not granted`() {
        assertTrue(isNotificationPermissionNeeded(isGranted = false, sdkInt = POST_NOTIFICATIONS_SDK))
    }

    @Test
    fun `no rationale when notifications are already granted`() {
        assertFalse(isNotificationPermissionNeeded(isGranted = true, sdkInt = POST_NOTIFICATIONS_SDK))
    }

    @Test
    fun `no rationale before Android 13`() {
        assertFalse(isNotificationPermissionNeeded(isGranted = false, sdkInt = POST_NOTIFICATIONS_SDK - 1))
    }
}
