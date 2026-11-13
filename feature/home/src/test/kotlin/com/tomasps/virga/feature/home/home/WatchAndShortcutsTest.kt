package com.tomasps.virga.feature.home.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchAndShortcutsTest {

    @Test
    fun `the notification rationale is needed on Android 13 and later when permission is not granted`() {
        assertTrue(isNotificationPermissionNeeded(isGranted = false, sdkInt = POST_NOTIFICATIONS_SDK))
        assertFalse(isNotificationPermissionNeeded(isGranted = true, sdkInt = POST_NOTIFICATIONS_SDK))
    }

    @Test
    fun `the notification rationale is never needed before Android 13`() {
        assertFalse(isNotificationPermissionNeeded(isGranted = false, sdkInt = POST_NOTIFICATIONS_SDK - 1))
    }
}
