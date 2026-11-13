package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.testing.DEFAULT_DEMO_ACCOUNTS
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.kv.KvKeyDetail
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_ASSETS
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_SESSIONS
import com.tomasps.virga.feature.kv.demo.KvDemoEntry
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.value.KV_HEX_PREVIEW_BYTES
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val BANNER = "banner text"
private const val BANNER_VALUE = "Scheduled maintenance on Sunday"
private const val JSON_KEY = "checkout/new-flow"
private const val JSON_VALUE_COMPACT = "{\"enabled\":true,\"rollout\":25}"
private const val JSON_VALUE_PRETTY = "{\n    \"enabled\": true,\n    \"rollout\": 25\n}"
private const val BINARY_KEY = "blobs/thumb.bin"
private const val BINARY_HEX = "89 50 4e 47 0d 0a 1a 0a 00 00 00 0d ff 00 7f 01"
private const val SESSION_KEY = "session:7f3a0c"
private const val SESSION_VALUE = "{\"user\":\"demo-user-17\",\"issued\":\"2026-11-11T08:00:00Z\"}"
private const val TEXT_LABEL = "Text value"
private const val METADATA_LABEL = "Metadata (JSON)"
private const val TTL_MODE = "After a number of seconds"
private const val TTL_LABEL = "Seconds from now"
private const val TTL_SHORT_MESSAGE = "A time to live must be at least 60 seconds."
private const val INVALID_METADATA_MESSAGE = "Metadata must be a JSON value. Check the brackets and quotes."
private const val RATE_LIMIT_MESSAGE =
    "Too many writes to this key. KV allows one write per second per key. Wait a moment and try again."
private const val SAVED_MESSAGE = "Value saved."
private const val ONE_SECOND_MILLIS = 1_000L
private const val SESSION_TTL_SLACK_SECONDS = 5L

/** One key's screen in demo mode: value views, text, metadata and expiration edits, delete, and the per-key limit. */
@RunWith(RobolectricTestRunner::class)
class KvKeyDetailFlowTest {

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

    private fun store(): KvDemoStore = flow.get<KvDemoStore>()

    private fun entry(key: String, namespaceId: String = KV_DEMO_NS_FLAGS) =
        store().entries.get(store().entryKey(KV_DEMO_ACCOUNT_PERSONAL, namespaceId, key))

    @Test
    fun `a text value is shown as it is stored`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, BANNER))

        flow.clickText("Show value")

        flow.assertTextShown(BANNER_VALUE)
        flow.assertTextShown("Edit text")
    }

    @Test
    fun `a JSON value is shown pretty-printed`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, JSON_KEY))

        flow.clickText("Show value")

        flow.assertTextShown(JSON_VALUE_PRETTY)
        assertTrue(JSON_VALUE_COMPACT != JSON_VALUE_PRETTY)
    }

    @Test
    fun `a binary value shows its size and a hex preview, and no edit button`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_ASSETS, BINARY_KEY))

        flow.clickText("Show value")

        flow.assertTextShown("16 B")
        flow.assertTextShown("This value is not valid UTF-8 text. The bytes are shown in hex.")
        flow.assertTextShown(BINARY_HEX)
        flow.waitUntilTextGone("Edit text")
    }

    @Test
    fun `a binary value longer than the hex preview says so`() {
        val big = ByteArray(KV_HEX_PREVIEW_BYTES + 10) { 0xFF.toByte() }
        store().entries.put(store().entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_ASSETS, "blobs/big.bin"), KvDemoEntry(big, null, null))
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_ASSETS, "blobs/big.bin"))

        flow.clickText("Show value")

        flow.assertTextShown("Showing the first $KV_HEX_PREVIEW_BYTES bytes.")
    }

    @Test
    fun `editing the text rewrites the value, and the new text is shown`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, BANNER))
        flow.clickText("Show value")

        flow.clickText("Edit text")
        flow.typeIntoField(TEXT_LABEL, "New banner")
        flow.clickText("Save")

        flow.waitForText(SAVED_MESSAGE)
        flow.assertTextShown("New banner")
        assertEquals("New banner", String(entry(BANNER)!!.bytes))
    }

    @Test
    fun `cancelling a text edit keeps the stored value`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, BANNER))
        flow.clickText("Show value")
        flow.clickText("Edit text")
        flow.typeIntoField(TEXT_LABEL, "not saved")

        flow.clickText("Cancel")

        flow.assertTextShown(BANNER_VALUE)
        assertEquals(BANNER_VALUE, String(entry(BANNER)!!.bytes))
    }

    @Test
    fun `an edit keeps the key's metadata and expiration`() {
        val before = entry(SESSION_KEY, KV_DEMO_NS_SESSIONS)!!
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_SESSIONS, SESSION_KEY))
        flow.clickText("Show value")
        flow.clickText("Edit text")
        flow.typeIntoField(TEXT_LABEL, SESSION_VALUE.replace("}", ",\"edited\":true}"))

        flow.clickText("Save")

        flow.waitForText(SAVED_MESSAGE)
        val after = entry(SESSION_KEY, KV_DEMO_NS_SESSIONS)!!
        assertEquals(before.metadata, after.metadata)
        assertEquals(before.expiresAtEpochSeconds, after.expiresAtEpochSeconds)
        assertTrue(String(after.bytes).contains("edited"))
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `the metadata of a key with none shows the empty note, and saving new metadata shows it pretty-printed`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, JSON_KEY))
        flow.assertTextShown("No metadata.")

        flow.clickText("Edit metadata")
        flow.typeIntoField(METADATA_LABEL, "{\"owner\":\"flags-team\"}")
        flow.clickText("Save")

        flow.waitUntilTextGone(METADATA_LABEL)
        flow.assertTextShown("{\n    \"owner\": \"flags-team\"\n}")
        assertEquals(buildJsonObject { put("owner", "flags-team") }, entry(JSON_KEY)!!.metadata)
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `invalid metadata is refused with its message and the editor stays open`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, JSON_KEY))
        flow.clickText("Edit metadata")
        flow.typeIntoField(METADATA_LABEL, "{oops")

        flow.clickText("Save")

        flow.waitForText(INVALID_METADATA_MESSAGE)
        flow.assertTextShown(METADATA_LABEL)
        assertNull(entry(JSON_KEY)!!.metadata)
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `cancelling the metadata editor changes nothing`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, JSON_KEY))
        flow.clickText("Edit metadata")
        flow.typeIntoField(METADATA_LABEL, "{\"owner\":\"nobody\"}")

        flow.clickText("Cancel")

        flow.waitUntilTextGone(METADATA_LABEL)
        assertNull(entry(JSON_KEY)!!.metadata)
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `a TTL under 60 seconds is refused, and a TTL of 120 seconds sets the expiration`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, BANNER))
        flow.clickText("Edit expiration")
        flow.clickText(TTL_MODE)
        flow.typeIntoField(TTL_LABEL, "30")
        flow.clickText("Save")
        flow.waitForText(TTL_SHORT_MESSAGE)
        assertNull(entry(BANNER)!!.expiresAtEpochSeconds)

        flow.typeIntoField(TTL_LABEL, "120")
        flow.clickText("Save")

        flow.waitUntilTextGone(TTL_LABEL)
        val expires = entry(BANNER)!!.expiresAtEpochSeconds!!
        val now = System.currentTimeMillis() / ONE_SECOND_MILLIS
        assertTrue(expires in (now + 120 - SESSION_TTL_SLACK_SECONDS)..(now + 120 + SESSION_TTL_SLACK_SECONDS))
    }

    @Test
    fun `the delete button asks first, cancel keeps the key, and confirm deletes it and returns to the list`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS), KvKeyDetail(KV_DEMO_NS_FLAGS, BANNER))
        flow.clickText("Delete key")
        flow.waitForText("Delete key?")
        flow.assertTextShown("Delete the key $BANNER? Its value and metadata are removed.")
        flow.clickText("Cancel")
        flow.waitUntilTextGone("Delete key?")
        assertNotNull(entry(BANNER))

        flow.clickText("Delete key")
        flow.clickText("Delete")

        flow.waitUntilTextGone("Delete key?")
        flow.waitForText("Feature flags")
        flow.waitUntilTextGone(BANNER)
        assertNull(entry(BANNER))
    }

    @Test
    fun `a key that does not exist shows the key-not-found state`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, "no/such/key"))

        flow.assertTextShown("Key not found")
        flow.assertTextShown("This key no longer exists. It may have been deleted or expired.")
    }

    @Test
    fun `a second text save within one second is refused by the per-key limit, and works after a second`() {
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, BANNER))
        flow.clickText("Show value")
        flow.clickText("Edit text")
        flow.typeIntoField(TEXT_LABEL, "one")
        flow.clickText("Save")
        flow.waitForText(SAVED_MESSAGE)

        flow.clickText("Edit text")
        flow.typeIntoField(TEXT_LABEL, "two")
        flow.clickText("Save")
        flow.waitForText(RATE_LIMIT_MESSAGE)
        assertEquals("one", String(entry(BANNER)!!.bytes))

        clock.nowMillis += ONE_SECOND_MILLIS
        flow.clickText("Save")
        flow.waitUntilTextGone(RATE_LIMIT_MESSAGE)
        assertEquals("two", String(entry(BANNER)!!.bytes))
    }

    @Test
    fun `a key the account cannot see shows the not-found error, without a retry`() {
        val other = CloudflareAccount(id = "not-a-demo-account", name = "Other")
        flow.close()
        flow = DemoFlow(
            compose,
            kvFeatureUnderTest(clock),
            accounts = DEFAULT_DEMO_ACCOUNTS + other,
            selectedAccountId = other.id,
        )
        flow.showSettled(KvKeyDetail(KV_DEMO_NS_FLAGS, BANNER))

        flow.assertTextShown("Not found")
        flow.assertTextShown("That item no longer exists, or this token cannot see it.")
        flow.waitUntilTextGone("Retry")
    }
}
