package com.tomasps.virga.feature.account.md3

import android.app.KeyguardManager
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tomasps.virga.core.navigation.Settings
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.ui.components.DefaultTimeRangeSource
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.feature.account.AccountAbout
import com.tomasps.virga.feature.account.settings.ThemeMode
import com.tomasps.virga.feature.account.settings.ThemeRepository
import com.tomasps.virga.feature.account.ui.accountFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val SAVE_TIMEOUT_MS = 10_000L

/** Material 3 audit: the settings the screen offers, each saved and read back. */
@RunWith(RobolectricTestRunner::class)
class Md3SettingsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow
    private lateinit var prefs: ThemeRepository

    @Before
    fun setUp() {
        flow = accountFlow(compose)
        prefs = flow.get()
        // Preferences live in a real DataStore that outlives a test, so start each test from the defaults.
        runBlocking {
            prefs.set(ThemeMode.System)
            prefs.resetAll()
        }
    }

    @After
    fun tearDown() {
        flow.close()
    }

    private fun <T> awaitSaved(flowOf: Flow<T>, expected: T) {
        flow.waitUntil(SAVE_TIMEOUT_MS) { runBlocking { flowOf.first() == expected } }
        assertEquals(expected, runBlocking { flowOf.first() })
    }

    private fun clickInDialog(text: String) {
        flow.waitForText(text)
        compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `theme choices are a single-choice group of radio buttons`() {
        flow.show(Settings)
        flow.waitForText("Dark")

        listOf("Follow system", "Light", "Dark").forEach { label ->
            val node = compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode()
            assertEquals("$label role", Role.RadioButton, node.config.getOrNull(SemanticsProperties.Role))
        }
    }

    @Test
    fun `dynamic color can be turned on`() {
        flow.show(Settings)

        flow.clickText("Dynamic color")

        awaitSaved(prefs.dynamicColor, true)
    }

    @Test
    fun `pure black can be turned on and off`() {
        flow.show(Settings)

        flow.clickText("Pure black")
        awaitSaved(prefs.pureBlack, true)

        flow.clickText("Pure black")
        awaitSaved(prefs.pureBlack, false)
    }

    @Test
    fun `settings switches are exposed as switches`() {
        flow.show(Settings)
        flow.waitForText("Pure black")

        listOf("Dynamic color", "Pure black", "Block screenshots").forEach { label ->
            val node = compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode()
            assertEquals("$label role", Role.Switch, node.config.getOrNull(SemanticsProperties.Role))
        }
    }

    @Test
    fun `the default analytics range can be changed`() {
        flow.show(Settings)

        flow.clickText("Default time range")
        val sevenDays = compose.onAllNodes(hasText("7d") and hasClickAction()).fetchSemanticsNodes()
        assertTrue("No 7d choice after opening the default range", sevenDays.isNotEmpty())
        flow.clickText("7d")

        awaitSaved(prefs.defaultTimeRange, TimeRange.SEVEN_DAYS)
    }

    @Test
    fun `the default range is served to other features`() {
        runBlocking { prefs.setDefaultTimeRange(TimeRange.THIRTY_DAYS) }

        val source = flow.get<DefaultTimeRangeSource>()

        assertEquals(TimeRange.THIRTY_DAYS, runBlocking { source.defaultRange.first() })
    }

    @Test
    fun `block screenshots can be turned on`() {
        flow.show(Settings)

        flow.clickText("Block screenshots")

        awaitSaved(prefs.blockScreenshots, true)
    }

    @Test
    fun `app lock can be turned on when the device has a screen lock`() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        flow.show(Settings)

        flow.clickText("App lock")

        awaitSaved(prefs.appLock, true)
    }

    @Test
    fun `notifications opens the system notification settings for the app`() {
        flow.show(Settings)

        flow.clickText("Notifications")

        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(ACTION_APP_NOTIFICATION_SETTINGS, started?.action)
    }

    @Test
    fun `about opens the about screen`() {
        flow.show(Settings)

        flow.clickText("About Virga")

        flow.assertNavigatedTo(AccountAbout)
    }

    @Test
    fun `reset settings asks first, then restores every default`() {
        runBlocking {
            prefs.set(ThemeMode.Dark)
            prefs.setPureBlack(true)
            prefs.setDynamicColor(true)
            prefs.setBlockScreenshots(true)
            prefs.setDefaultTimeRange(TimeRange.SEVEN_DAYS)
            prefs.setAppLock(true)
        }
        flow.show(Settings)

        flow.clickText("Reset settings")
        clickInDialog("Reset")

        awaitSaved(prefs.pureBlack, false)
        awaitSaved(prefs.mode, ThemeMode.System)
        awaitSaved(prefs.dynamicColor, false)
        awaitSaved(prefs.blockScreenshots, false)
        awaitSaved(prefs.defaultTimeRange, TimeRange.TWENTY_FOUR_HOURS)
        awaitSaved(prefs.appLock, false)
    }

    @Test
    fun `settings are grouped under headed sections`() {
        flow.show(Settings)
        flow.waitForText("Appearance")

        listOf("Appearance", "Analytics", "Privacy", "About").forEach { title ->
            val headers = compose.onAllNodes(hasText(title) and isHeading()).fetchSemanticsNodes()
            assertTrue("No heading '$title'", headers.isNotEmpty())
        }
    }

    @Test
    fun `settings shows the app version`() {
        flow.show(Settings)
        flow.waitForText("Version")

        val version = compose.onNode(hasText("Version")).apply { performScrollTo() }.fetchSemanticsNode()
        assertFalse(version.boundsInRoot.isEmpty)
    }
}
