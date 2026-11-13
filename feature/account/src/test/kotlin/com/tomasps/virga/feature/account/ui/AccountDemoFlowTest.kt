package com.tomasps.virga.feature.account.ui

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.navigation.AccountRoot
import com.tomasps.virga.core.navigation.Settings
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.account.AccountAbout
import com.tomasps.virga.feature.account.AccountSwitcher
import com.tomasps.virga.feature.account.settings.ThemeMode
import com.tomasps.virga.feature.account.settings.ThemeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PERSONAL_NAME = "Personal (demo)"
private const val STAGING_NAME = "Staging (demo)"
private const val PERSONAL_ID = "demo-account-personal"
private const val STAGING_ID = "demo-account-staging"
private const val DEMO_BADGE = "Demo account, sample data"
private const val SIGN_OUT_TITLE = "Sign out of Virga?"
private const val THEME_SAVE_TIMEOUT_MS = 10_000L

/** Account tab, switcher, settings and about, in demo mode, through the feature's real graph and screens. */
@RunWith(RobolectricTestRunner::class)
class AccountDemoFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = accountFlow(compose)
        // The theme lives in a real DataStore that outlives a test, so start each test from the default.
        runBlocking { flow.get<ThemeRepository>().set(ThemeMode.System) }
    }

    @After
    fun tearDown() {
        flow.close()
    }

    // Account tab

    @Test
    fun `account tab shows the selected demo account, its id and a friendly type and date`() {
        flow.show(AccountRoot)

        flow.assertTextShown(PERSONAL_NAME)
        flow.assertTextShown(DEMO_BADGE)
        flow.assertTextShown(PERSONAL_ID)
        flow.assertTextShown("Standard")
        assertTrue(compose.onAllNodes(hasText("2024", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, compose.onAllNodes(hasText("T09:00:00", substring = true)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("standard")).fetchSemanticsNodes().size)
    }

    @Test
    fun `switch account row opens the switcher`() {
        flow.show(AccountRoot)

        flow.clickText("Switch account")

        flow.assertNavigatedTo(AccountSwitcher)
        flow.waitForText(STAGING_NAME)
    }

    @Test
    fun `settings row opens settings`() {
        flow.show(AccountRoot)

        flow.clickText("Settings")

        flow.assertNavigatedTo(Settings)
        flow.waitForText("Appearance")
    }

    @Test
    fun `about row opens about`() {
        flow.show(AccountRoot)

        flow.clickText("About Virga")

        flow.assertNavigatedTo(AccountAbout)
        flow.waitForText("Virga is not affiliated with, endorsed by, or sponsored by Cloudflare, Inc.")
    }

    // Switcher

    @Test
    fun `switcher lists both demo accounts and marks the selected one`() {
        flow.showStack(AccountRoot, AccountSwitcher)

        flow.assertTextShown(PERSONAL_NAME)
        flow.assertTextShown(STAGING_NAME)
        flow.assertTextShown(PERSONAL_ID)
        flow.assertTextShown(STAGING_ID)
        compose.onNodeWithContentDescription("Selected").assertIsDisplayed()
    }

    @Test
    fun `choosing another account changes the selection and returns to the account tab`() {
        flow.showStack(AccountRoot, AccountSwitcher)

        flow.clickText(STAGING_NAME)

        flow.waitUntilTextGone(PERSONAL_NAME)
        flow.assertTextShown(STAGING_ID)
        val selected = flow.get<SessionRepository>().state.value as SessionState.SignedIn
        assertEquals(STAGING_ID, selected.selectedAccountId)
    }

    @Test
    fun `back from the switcher keeps the current account`() {
        flow.showStack(AccountRoot, AccountSwitcher)

        compose.onNodeWithContentDescription("Back").performClick()

        flow.waitForText(PERSONAL_NAME)
        flow.assertTextShown(PERSONAL_ID)
        val selected = flow.get<SessionRepository>().state.value as SessionState.SignedIn
        assertEquals(PERSONAL_ID, selected.selectedAccountId)
    }

    // Settings: appearance

    @Test
    fun `choosing a theme is saved and read back`() {
        flow.show(Settings)

        flow.clickText("Light")

        val themes = flow.get<ThemeRepository>()
        compose.waitUntil(THEME_SAVE_TIMEOUT_MS) { runBlocking { themes.mode.first() == ThemeMode.Light } }
        assertEquals(ThemeMode.Light, runBlocking { themes.mode.first() })
    }

    @Test
    fun `dark and then follow system are each saved in turn`() {
        flow.show(Settings)
        val themes = flow.get<ThemeRepository>()

        flow.clickText("Dark")
        awaitSavedTheme(themes, ThemeMode.Dark)
        // The saved value comes back to the screen through the ViewModel; let that settle before the next tap.
        compose.waitForIdle()

        flow.clickText("Follow system")
        awaitSavedTheme(themes, ThemeMode.System)
    }

    /** Waits for [expected] to be stored, and on timeout says what was stored and what the screen showed. */
    private fun awaitSavedTheme(themes: ThemeRepository, expected: ThemeMode) {
        try {
            compose.waitUntil(THEME_SAVE_TIMEOUT_MS) { runBlocking { themes.mode.first() == expected } }
        } catch (timeout: ComposeTimeoutException) {
            val stored = runBlocking { themes.mode.first() }
            val screen = compose.onRoot().printToString(maxDepth = Int.MAX_VALUE)
            throw AssertionError("Expected saved theme $expected but it is $stored. Screen:\n$screen", timeout)
        }
    }

    @Test
    fun `the appearance default is follow system`() {
        flow.show(Settings)

        flow.waitForText("Follow system")
        assertEquals(ThemeMode.System, runBlocking { flow.get<ThemeRepository>().mode.first() })
    }

    @Test
    fun `section header text lines up with row text on the settings screen`() {
        flow.show(Settings)

        val header = compose.onNodeWithText("Appearance", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rowText = compose.onNodeWithText("Follow system", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertEquals(header.left, rowText.left, 0.5f)
    }

    // Settings: sign out confirmation

    @Test
    fun `sign out asks for confirmation and cancel keeps the session`() {
        flow.show(Settings)

        flow.clickText("Sign out")
        flow.assertTextShown(SIGN_OUT_TITLE)
        clickInDialog("Cancel")

        flow.waitUntilTextGone(SIGN_OUT_TITLE)
        assertTrue(flow.get<SessionRepository>().state.value is SessionState.SignedIn)
    }

    @Test
    fun `confirming sign out signs the session out`() {
        flow.show(Settings)

        flow.clickText("Sign out")
        flow.assertTextShown(SIGN_OUT_TITLE)
        clickInDialog("Sign out")

        flow.waitUntilTextGone(SIGN_OUT_TITLE)
        assertEquals(SessionState.SignedOut, flow.get<SessionRepository>().state.value)
    }

    @Test
    fun `settings back returns to the account tab`() {
        flow.showStack(AccountRoot, Settings)

        compose.onNodeWithContentDescription("Back").performClick()

        flow.waitForText(PERSONAL_NAME)
        assertEquals(0, compose.onAllNodes(hasText("Appearance")).fetchSemanticsNodes().size)
    }

    // About

    @Test
    fun `about shows version, not-affiliated wording and the trademark line`() {
        flow.show(AccountAbout)

        flow.waitForText("Unofficial client for Cloudflare")
        compose.onNodeWithText("Version", substring = true).assertIsDisplayed()
        flow.assertTextShown("Virga is not affiliated with, endorsed by, or sponsored by Cloudflare, Inc.")
        flow.assertTextShown(
            "Cloudflare, the Cloudflare logo, and Cloudflare Workers are trademarks and/or registered trademarks of Cloudflare, Inc.",
        )
    }

    private fun clickInDialog(text: String) {
        flow.waitForText(text)
        compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
    }
}
