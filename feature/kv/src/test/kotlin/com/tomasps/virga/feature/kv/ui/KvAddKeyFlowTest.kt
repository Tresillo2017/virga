package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.kv.KvAddKey
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

private const val NAME_LABEL = "Key name"
private const val VALUE_LABEL = "Value"
private const val METADATA_LABEL = "Metadata (JSON)"
private const val EXPIRY_LABEL = "Expires at (yyyy-MM-dd HH:mm)"
private const val TTL_LABEL = "Seconds from now"
private const val SAVE = "Save key"
private const val ADD_KEY_TITLE = "Add key"
private const val TTL_MODE = "After a number of seconds"
private const val ABSOLUTE_MODE = "At a date and time"
private const val FILE_CHIP = "File"
private const val TEXT_CHIP = "Text"
private const val TOO_LARGE_METADATA_MESSAGE = "Metadata is larger than 1,024 bytes."
private const val TTL_SHORT_MESSAGE = "A time to live must be at least 60 seconds."
private const val ABSOLUTE_SOON_MESSAGE = "That time is less than 60 seconds from now."
private const val NOT_A_NUMBER_MESSAGE = "Enter a whole number of seconds."
private const val NOT_A_DATE_MESSAGE = "Enter a date and time as yyyy-MM-dd HH:mm."
private const val NAME_EMPTY_MESSAGE = "Enter a key name."
private const val DOT_MESSAGE = "The names . and .. cannot be used as keys."
private const val TOO_LONG_MESSAGE = "The key name is longer than 512 bytes."
private const val INVALID_METADATA_MESSAGE = "Metadata must be a JSON value. Check the brackets and quotes."
private const val OVERWRITE_TITLE = "Replace this key?"
private const val EXISTING_KEY = "checkout/new-flow"
private const val FAR_FUTURE = "2099-01-01 00:00"
private const val TTL_LIMIT_SECONDS = 60L

/** Add-key screen in demo mode: every validation message, both expiration modes, the overwrite prompt and the save. */
@RunWith(RobolectricTestRunner::class)
class KvAddKeyFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var clock: FakeMillisClock
    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        clock = FakeMillisClock()
        quickIdleTimeout()
        flow = kvDemoFlow(compose, clock)
    }

    @After
    fun tearDown() {
        flow.close()
        restoreIdleTimeout()
    }

    private fun openAddKey() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS), KvAddKey(KV_DEMO_NS_FLAGS))
    }

    @Test
    fun `add key with invalid metadata shows the validation message and writes nothing`() {
        openAddKey()

        flow.typeIntoField(NAME_LABEL, "flow/invalid-metadata")
        flow.typeIntoField(VALUE_LABEL, "hello")
        flow.typeIntoField(METADATA_LABEL, "{not json")
        flow.clickText(SAVE)

        flow.waitForText(INVALID_METADATA_MESSAGE)
        flow.assertTextShown(ADD_KEY_TITLE)
        assertNull(flow.storedEntry("flow/invalid-metadata"))
    }

    @Test
    fun `add key with valid input returns to the namespace and lists the new key`() {
        openAddKey()

        flow.typeIntoField(NAME_LABEL, "flow/new-key")
        flow.typeIntoField(VALUE_LABEL, "hello from the flow test")
        flow.clickText(SAVE)

        flow.waitUntilTextGone(SAVE)
        flow.waitForText("Feature flags")
        flow.assertTextShown("flow/new-key")
        assertEquals("hello from the flow test", String(flow.storedEntry("flow/new-key")!!.bytes))
    }

    @Test
    fun `a saved key returns to the namespace even while an earlier validation message is still showing`() {
        openAddKey()
        flow.typeIntoField(NAME_LABEL, "flow/late-navigation")
        flow.typeIntoField(VALUE_LABEL, "v")
        flow.typeIntoField(METADATA_LABEL, "{not json")
        flow.clickText(SAVE)
        flow.waitForText(INVALID_METADATA_MESSAGE)

        // The snackbar stays up on the paused clock. Fixing the metadata and saving must still leave the screen.
        flow.typeIntoField(METADATA_LABEL, "{\"owner\":\"flow\"}")
        flow.clickText(SAVE)

        flow.waitUntilTextGone(SAVE)
        flow.assertTextShown("flow/late-navigation")
    }

    @Test
    fun `a blank key name is refused with its message`() {
        openAddKey()
        flow.typeIntoField(VALUE_LABEL, "v")
        flow.clickText(SAVE)

        flow.waitForText(NAME_EMPTY_MESSAGE)
        assertNull(flow.storedEntry(""))
    }

    @Test
    fun `the dot and dot-dot names are refused with the dot message`() {
        openAddKey()
        flow.typeIntoField(VALUE_LABEL, "v")

        flow.typeIntoField(NAME_LABEL, ".")
        flow.clickText(SAVE)
        flow.waitForText(DOT_MESSAGE)

        flow.typeIntoField(NAME_LABEL, "..")
        flow.clickText(SAVE)
        flow.advanceClock(SNACKBAR_CLEAR_MILLIS)
        flow.waitForText(DOT_MESSAGE)

        assertNull(flow.storedEntry("."))
        assertNull(flow.storedEntry(".."))
    }

    @Test
    fun `a key name over 512 bytes is refused, and a 512 byte name is accepted`() {
        openAddKey()
        flow.typeIntoField(VALUE_LABEL, "v")
        flow.typeIntoField(NAME_LABEL, "k".repeat(513))
        flow.clickText(SAVE)
        flow.waitForText(TOO_LONG_MESSAGE)
        assertNull(flow.storedEntry("k".repeat(513)))

        flow.typeIntoField(NAME_LABEL, "k".repeat(512))
        flow.clickText(SAVE)
        flow.waitUntilTextGone(SAVE)
        assertNotNull(flow.storedEntry("k".repeat(512)))
    }

    @Test
    fun `metadata over 1024 bytes is refused and nothing is written`() {
        openAddKey()
        flow.typeIntoField(NAME_LABEL, "flow/big-metadata")
        flow.typeIntoField(VALUE_LABEL, "v")
        flow.typeIntoField(METADATA_LABEL, "{\"note\":\"" + "a".repeat(1_100) + "\"}")
        flow.clickText(SAVE)

        flow.waitForText(TOO_LARGE_METADATA_MESSAGE)
        assertNull(flow.storedEntry("flow/big-metadata"))
    }

    @Test
    fun `a TTL of 59 seconds is refused and a TTL of 60 seconds is saved with an expiration`() {
        openAddKey()
        flow.clickText(TTL_MODE)
        flow.waitForText(TTL_LABEL)
        flow.typeIntoField(NAME_LABEL, "flow/ttl")
        flow.typeIntoField(VALUE_LABEL, "v")
        flow.typeIntoField(TTL_LABEL, "59")
        flow.clickText(SAVE)
        flow.waitForText(TTL_SHORT_MESSAGE)
        assertNull(flow.storedEntry("flow/ttl"))

        flow.typeIntoField(TTL_LABEL, "60")
        flow.clickText(SAVE)

        flow.waitUntilTextGone(SAVE)
        val expires = flow.storedEntry("flow/ttl")?.expiresAtEpochSeconds
        assertNotNull(expires)
        val now = System.currentTimeMillis() / MILLIS
        assertTrue(expires!! in (now + TTL_LIMIT_SECONDS - SLACK_SECONDS)..(now + TTL_LIMIT_SECONDS + SLACK_SECONDS))
    }

    @Test
    fun `a TTL that is not a whole number is refused with its message`() {
        openAddKey()
        flow.clickText(TTL_MODE)
        flow.typeIntoField(NAME_LABEL, "flow/ttl-text")
        flow.typeIntoField(VALUE_LABEL, "v")
        flow.typeIntoField(TTL_LABEL, "soon")
        flow.clickText(SAVE)

        flow.waitForText(NOT_A_NUMBER_MESSAGE)
        assertNull(flow.storedEntry("flow/ttl-text"))
    }

    @Test
    fun `an absolute time less than 60 seconds away is refused`() {
        openAddKey()
        flow.clickText(ABSOLUTE_MODE)
        flow.typeIntoField(NAME_LABEL, "flow/soon")
        flow.typeIntoField(VALUE_LABEL, "v")
        flow.typeIntoField(EXPIRY_LABEL, "2000-01-01 00:00")
        flow.clickText(SAVE)

        flow.waitForText(ABSOLUTE_SOON_MESSAGE)
        assertNull(flow.storedEntry("flow/soon"))
    }

    @Test
    fun `an absolute time with the wrong shape is refused with its message`() {
        openAddKey()
        flow.clickText(ABSOLUTE_MODE)
        flow.typeIntoField(NAME_LABEL, "flow/shape")
        flow.typeIntoField(VALUE_LABEL, "v")
        flow.typeIntoField(EXPIRY_LABEL, "tomorrow")
        flow.clickText(SAVE)

        flow.waitForText(NOT_A_DATE_MESSAGE)
        assertNull(flow.storedEntry("flow/shape"))
    }

    @Test
    fun `an absolute expiration in the future is saved at that local time`() {
        openAddKey()
        flow.clickText(ABSOLUTE_MODE)
        flow.typeIntoField(NAME_LABEL, "flow/absolute")
        flow.typeIntoField(VALUE_LABEL, "v")
        flow.typeIntoField(EXPIRY_LABEL, FAR_FUTURE)
        flow.clickText(SAVE)

        flow.waitUntilTextGone(SAVE)
        val expected = LocalDateTime.of(2099, 1, 1, 0, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
        assertEquals(expected, flow.storedEntry("flow/absolute")?.expiresAtEpochSeconds)
    }

    @Test
    fun `choosing a mode by tapping its label selects it`() {
        openAddKey()

        flow.clickText(TTL_MODE)

        flow.waitForText(TTL_LABEL)
    }

    @Test
    fun `the file source shows the picker and the size limit, and the text source shows the byte count`() {
        openAddKey()
        flow.assertTextShown("Value")

        flow.clickText(FILE_CHIP)
        flow.assertTextShown("Choose file")
        flow.assertTextShown("Limit: 25.0 MiB.")

        flow.clickText(TEXT_CHIP)
        flow.waitForText(VALUE_LABEL)
        flow.typeIntoField(VALUE_LABEL, "abc")
        flow.assertTextShown("3 B of 25.0 MiB")
    }

    @Test
    fun `replacing an existing key asks first, cancel keeps the old value, replace writes`() {
        openAddKey()
        flow.typeIntoField(NAME_LABEL, EXISTING_KEY)
        flow.typeIntoField(VALUE_LABEL, "replacement")
        flow.clickText(SAVE)

        flow.waitForText(OVERWRITE_TITLE)
        flow.clickText("Cancel")
        flow.waitUntilTextGone(OVERWRITE_TITLE)
        assertEquals("{\"enabled\":true,\"rollout\":25}", String(flow.storedEntry(EXISTING_KEY)!!.bytes))

        flow.clickText(SAVE)
        flow.waitForText(OVERWRITE_TITLE)
        flow.clickText("Replace")

        flow.waitUntilTextGone(SAVE)
        assertEquals("replacement", String(flow.storedEntry(EXISTING_KEY)!!.bytes))
    }

    private companion object {
        const val MILLIS = 1_000L
        const val SLACK_SECONDS = 5L
        const val SNACKBAR_CLEAR_MILLIS = 10_000L
    }
}
