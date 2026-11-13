package com.tomasps.virga.feature.r2.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.r2.r2KoinModule
import com.tomasps.virga.feature.r2.registerR2Entries
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ASSETS = "demo-assets"
private const val BACKUPS = "demo-backups"
private const val LOGS = "demo-logs"
private const val SETTINGS_TAB = "Settings"
private const val PUBLIC_ACCESS_SWITCH = "Public development URL (r2.dev)"
private const val PUBLIC_ACCESS_OFF = "Off. Intended for testing; rate limited."
private const val DOMAIN = "static.example.test"
private const val CORS_ORIGIN = "https://app.example.test"
private const val CORS_SUPPORTING = "Methods: GET, HEAD · max age 600 s"
private const val NEW_CORS_ORIGIN = "https://new.example.test"
private const val CORS_ORIGIN_INVALID = "Origins start with https:// or http://, or are *."
private const val LOCK_RULE = "keep-90-days"
private const val LIFECYCLE_RULE = "expire-app-logs"

/** The bucket Settings tab, in demo mode: each section, and the confirm-then-act paths that need no text field. */
@RunWith(RobolectricTestRunner::class)
class R2SettingsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = DemoFlow(compose, FeatureUnderTest(listOf(r2KoinModule), ::registerR2Entries))
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `settings show every section with what the bucket has`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText(SETTINGS_TAB)

        flow.waitForText(PUBLIC_ACCESS_OFF)
        flow.assertTextShown(DOMAIN)
        flow.assertTextShown(CORS_ORIGIN)
        flow.assertTextShown(CORS_SUPPORTING)
        flow.assertTextShown("No lock rules. Objects can be deleted or overwritten.")
        flow.assertTextShown("No lifecycle rules.")
        flow.assertTextShown("Event notifications")
    }

    @Test
    fun `the lock section lists a rule with its scope and retention`() {
        flow.show(R2Bucket(BACKUPS))
        flow.clickText(SETTINGS_TAB)

        flow.waitForText(LOCK_RULE)
        flow.assertTextShown("all objects · kept for 90 days")
    }

    @Test
    fun `the lifecycle section lists a rule with its prefix, expiry and state`() {
        flow.show(R2Bucket(LOGS))
        flow.clickText(SETTINGS_TAB)

        flow.waitForText(LIFECYCLE_RULE)
        flow.assertTextShown("app/ · delete after 30 days · enabled")
    }

    @Test
    fun `turning public access on asks first, and cancel leaves it off`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText(SETTINGS_TAB)
        flow.waitForText(PUBLIC_ACCESS_OFF)

        compose.clickDescription(PUBLIC_ACCESS_SWITCH)
        flow.waitForText("Allow public access?")
        compose.clickInDialog("Cancel")

        flow.waitUntilTextGone("Allow public access?")
        flow.assertTextShown(PUBLIC_ACCESS_OFF)
    }

    @Test
    fun `confirming public access turns it on and shows the public hostname`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText(SETTINGS_TAB)
        flow.waitForText(PUBLIC_ACCESS_OFF)

        compose.clickDescription(PUBLIC_ACCESS_SWITCH)
        flow.waitForText("Allow public access?")
        compose.clickInDialog("Allow")

        flow.waitForText("Public access updated.")
        flow.waitForText("pub-demo-assets.r2.dev")
    }

    @Test
    fun `removing a custom domain asks first and then removes it`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText(SETTINGS_TAB)
        flow.waitForText(DOMAIN)

        compose.clickDescription("Remove domain $DOMAIN")
        flow.waitForText("Remove $DOMAIN?")
        compose.clickInDialog("Remove")

        flow.waitForText("Custom domain removed.")
        flow.waitUntilTextGone(DOMAIN)
    }

    @Test
    fun `delete all CORS rules asks first and then leaves the empty state`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText(SETTINGS_TAB)
        flow.waitForText(CORS_ORIGIN)

        compose.clickDescription("Delete all CORS rules")
        flow.waitForText("Delete all CORS rules?")
        compose.clickInDialog("Delete all CORS rules")

        flow.waitForText("CORS rules deleted.")
        flow.waitUntilTextGone(CORS_ORIGIN)
        flow.assertTextShown("No CORS rules. Browsers cannot call this bucket from another site.")
    }

    @Test
    fun `opening a CORS rule shows its editor, and cancel closes it without a change`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText(SETTINGS_TAB)
        flow.waitForText(CORS_ORIGIN)

        flow.clickText(CORS_ORIGIN)
        flow.waitForText("Edit CORS rule")
        flow.clickText("Cancel")

        flow.waitUntilTextGone("Edit CORS rule")
        flow.assertTextShown(CORS_SUPPORTING)
    }

    @Test
    fun `removing a lock rule asks first and then leaves the empty state`() {
        flow.show(R2Bucket(BACKUPS))
        flow.clickText(SETTINGS_TAB)
        flow.waitForText(LOCK_RULE)

        compose.clickDescription("Remove lock rule $LOCK_RULE")
        flow.waitForText("Remove lock rule $LOCK_RULE?")
        compose.clickInDialog("Remove")

        flow.waitForText("Lock rule removed.")
        flow.waitUntilTextGone(LOCK_RULE)
    }

    @Test
    fun `removing a lifecycle rule asks first and then leaves the empty state`() {
        flow.show(R2Bucket(LOGS))
        flow.clickText(SETTINGS_TAB)
        flow.waitForText(LIFECYCLE_RULE)

        compose.clickDescription("Remove lifecycle rule $LIFECYCLE_RULE")
        flow.waitForText("Remove lifecycle rule?")
        compose.clickInDialog("Remove")

        flow.waitForText("Lifecycle rule removed.")
        flow.waitUntilTextGone(LIFECYCLE_RULE)
    }

    @Test
    fun `saving an edited CORS rule writes it back and shows the new origin`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText(SETTINGS_TAB)
        flow.waitForText(CORS_ORIGIN)

        flow.clickText(CORS_ORIGIN)
        flow.waitForText("Edit CORS rule")
        flow.typeIntoField("Allowed origins", NEW_CORS_ORIGIN)
        flow.clickText("Save")

        flow.waitForText("CORS rules saved.")
        flow.waitForText(NEW_CORS_ORIGIN)
        flow.waitUntilTextGone(CORS_ORIGIN)
    }

    @Test
    fun `a CORS origin without a scheme is refused in the editor and nothing is saved`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText(SETTINGS_TAB)
        flow.waitForText(CORS_ORIGIN)

        flow.clickText(CORS_ORIGIN)
        flow.waitForText("Edit CORS rule")
        flow.typeIntoField("Allowed origins", "example.test")
        flow.clickText("Save")

        flow.waitForText(CORS_ORIGIN_INVALID)
        flow.assertTextShown("Edit CORS rule")
    }
}
