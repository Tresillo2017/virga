@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.tomasps.virga.core.ui.md3

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tomasps.virga.core.ui.theme.VirgaDarkColorScheme
import com.tomasps.virga.core.ui.theme.VirgaLightColorScheme
import com.tomasps.virga.core.ui.theme.VirgaTheme
import com.tomasps.virga.core.ui.window.SecureWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Material 3 audit: what VirgaTheme hands to its content, and the secure-window switch. */
@RunWith(RobolectricTestRunner::class)
class Md3ThemeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun schemeOf(dark: Boolean, dynamicColor: Boolean = false, pureBlack: Boolean = false): ColorScheme {
        var scheme: ColorScheme? = null
        compose.setContent {
            VirgaTheme(darkTheme = dark, dynamicColor = dynamicColor, pureBlack = pureBlack) {
                scheme = MaterialTheme.colorScheme
            }
        }
        compose.waitForIdle()
        return checkNotNull(scheme)
    }

    @Test
    fun `the theme uses the expressive motion scheme`() {
        var motion: MotionScheme? = null
        compose.setContent { VirgaTheme(darkTheme = true) { motion = MaterialTheme.motionScheme } }
        compose.waitForIdle()
        val expressive = MotionScheme.expressive()
        assertEquals(expressive.defaultSpatialSpec<Float>(), checkNotNull(motion).defaultSpatialSpec<Float>())
        assertEquals(expressive.fastEffectsSpec<Float>(), checkNotNull(motion).fastEffectsSpec<Float>())
    }

    @Test
    fun `dynamic color replaces the dark palette on Android 12 and newer`() {
        val scheme = schemeOf(dark = true, dynamicColor = true)
        assertNotEquals(VirgaDarkColorScheme.primary, scheme.primary)
        assertNotEquals(VirgaDarkColorScheme.surface, scheme.surface)
    }

    @Test
    fun `dynamic color replaces the light palette on Android 12 and newer`() {
        val scheme = schemeOf(dark = false, dynamicColor = true)
        assertNotEquals(VirgaLightColorScheme.primary, scheme.primary)
    }

    @Test
    @Config(sdk = [30])
    fun `before Android 12 dynamic color falls back to the Virga palette`() {
        val scheme = schemeOf(dark = true, dynamicColor = true)
        assertEquals(VirgaDarkColorScheme.primary, scheme.primary)
    }

    @Test
    fun `pure black makes dark surfaces black`() {
        val scheme = schemeOf(dark = true, pureBlack = true)
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
        assertEquals(VirgaDarkColorScheme.primary, scheme.primary)
    }

    @Test
    fun `pure black also applies on top of dynamic color`() {
        val scheme = schemeOf(dark = true, dynamicColor = true, pureBlack = true)
        assertEquals(Color.Black, scheme.surface)
        assertNotEquals(VirgaDarkColorScheme.primary, scheme.primary)
    }

    @Test
    fun `pure black leaves the light theme alone`() {
        val scheme = schemeOf(dark = false, pureBlack = true)
        assertEquals(VirgaLightColorScheme.surface, scheme.surface)
    }

    @Test
    fun `secure window sets and clears FLAG_SECURE`() {
        var secure by mutableStateOf(true)
        compose.setContent { SecureWindow(enabled = secure) }
        compose.waitForIdle()
        val flags = { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE }
        assertTrue("FLAG_SECURE not set", flags() != 0)
        secure = false
        compose.waitForIdle()
        assertFalse("FLAG_SECURE not cleared", flags() != 0)
    }
}
