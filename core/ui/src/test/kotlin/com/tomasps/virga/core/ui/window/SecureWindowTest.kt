package com.tomasps.virga.core.ui.window

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureWindowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun isSecure(): Boolean =
        compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun `the window stays secure while any caller still asks`() {
        var setting by mutableStateOf(true)
        var tokenShown by mutableStateOf(true)
        compose.setContent {
            SecureWindow(enabled = setting)
            SecureWindow(enabled = tokenShown)
        }
        compose.waitForIdle()
        assertTrue(isSecure())

        tokenShown = false
        compose.waitForIdle()
        assertTrue("hiding the token must not clear the setting's flag", isSecure())

        setting = false
        compose.waitForIdle()
        assertFalse(isSecure())
    }
}
