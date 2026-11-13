package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.KvRoot
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.kv.KvKeyDetail
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoEntry
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.namespace.PREFIX_DEBOUNCE_MILLIS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val FLAGS_TITLE = "Feature flags"
private const val SEEDED_KEY = "banner text"
private const val SECOND_KEY = "a b+c"
private const val CHECKOUT_KEY = "checkout/new-flow"
private const val EXTRA_KEYS = 105
private const val CHECKOUT_LIST_INDEX = 108
private const val FOOTER_LIST_INDEX = 101
private const val SEARCH_TEXT_PREFIX = "checkout"
private const val DASHBOARD_ROW_LIST_INDEX = 6

/** One namespace's screen in demo mode: prefix search, paging, bulk delete, rename, delete and the metrics tab. */
@RunWith(RobolectricTestRunner::class)
class KvNamespaceFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        quickIdleTimeout()
        flow = kvDemoFlow(compose)
    }

    @After
    fun tearDown() {
        flow.close()
        restoreIdleTimeout()
    }

    private fun flagsStore(): KvDemoStore = flow.get<KvDemoStore>()

    private fun flagsKeyNames(): Set<String> {
        val store = flagsStore()
        val prefix = store.keyPrefix(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS)
        return store.entries.items.value.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }

    private fun typeSearch(text: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        flow.advanceClock(PREFIX_DEBOUNCE_MILLIS)
    }

    private fun openMenuItem(item: String) {
        compose.onNodeWithContentDescription("More actions").performClick()
        flow.clickText(item)
    }

    @Test
    fun `typing a prefix lists only the keys that start with it, and clearing it brings the rest back`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        flow.assertTextShown(SEEDED_KEY)

        typeSearch(SEARCH_TEXT_PREFIX)
        flow.assertTextShown(CHECKOUT_KEY)
        flow.waitUntilTextGone(SEEDED_KEY)

        flow.clickText("Clear search")
        flow.advanceClock(PREFIX_DEBOUNCE_MILLIS)
        flow.assertTextShown(SEEDED_KEY)
    }

    @Test
    fun `a prefix that matches nothing shows the no-matching-keys state`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        flow.assertTextShown(SEEDED_KEY)

        typeSearch("zzz-nothing")

        flow.assertTextShown("No matching keys")
        flow.assertTextShown("No key starts with this prefix.")
    }

    @Test
    fun `an empty namespace shows the no-keys state with the add hint`() {
        val store = flagsStore()
        flagsKeyNames().forEach { store.entries.remove(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, it)) }
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))

        flow.assertTextShown("No keys")
        flow.assertTextShown("Add a key with the + button, or import a JSON file.")
    }

    @Test
    fun `load more appends the next page, and the last page removes the footer`() {
        seedExtraKeys()
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(FOOTER_LIST_INDEX)

        flow.clickText("Load more")
        flow.waitUntilTextGone("Load more")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(CHECKOUT_LIST_INDEX)

        flow.clickText(CHECKOUT_KEY)
        flow.assertNavigatedTo(KvKeyDetail(KV_DEMO_NS_FLAGS, CHECKOUT_KEY))
    }

    @Test
    fun `a long press on a key starts a selection of that key`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        flow.assertTextShown(SEEDED_KEY)

        compose.onNode(hasText(SEEDED_KEY) and hasClickAction()).performTouchInput { longClick() }

        flow.assertTextShown("1 key selected")
        assertEquals(emptyList<Any>(), flow.history)
    }

    @Test
    fun `while keys are selected a tap toggles them, and Clear empties the selection`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        compose.onNode(hasText(SEEDED_KEY) and hasClickAction()).performTouchInput { longClick() }

        flow.clickText(SECOND_KEY)
        flow.assertTextShown("2 keys selected")
        flow.clickText(SECOND_KEY)
        flow.assertTextShown("1 key selected")

        flow.clickText("Clear")
        flow.waitUntilTextGone("1 key selected")
        flow.assertTextShown(SEEDED_KEY)
    }

    @Test
    fun `bulk delete asks first, cancel keeps the keys, and confirm removes them`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        compose.onNode(hasText(SEEDED_KEY) and hasClickAction()).performTouchInput { longClick() }
        flow.clickText(SECOND_KEY)

        flow.clickText("Delete selected")
        flow.waitForText("Delete selected keys?")
        flow.clickText("Cancel")
        flow.waitUntilTextGone("Delete selected keys?")
        assertEquals(true, flagsKeyNames().containsAll(setOf(SEEDED_KEY, SECOND_KEY)))

        flow.clickText("Delete selected")
        flow.clickText("Delete")

        flow.waitForText("Deleted 2 keys.")
        flow.waitUntilTextGone(SECOND_KEY)
        assertEquals(false, flagsKeyNames().contains(SECOND_KEY))
        assertEquals(false, flagsKeyNames().contains(SEEDED_KEY))
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `rename shows the current title, cancel keeps it, and save changes the title`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        flow.assertTextShown(FLAGS_TITLE)

        openMenuItem("Rename namespace")
        flow.waitForText("Rename namespace")
        flow.clickText("Cancel")
        flow.waitUntilTextGone("Rename namespace")
        flow.assertTextShown(FLAGS_TITLE)

        openMenuItem("Rename namespace")
        flow.typeIntoField("Title", "Flags v2")
        flow.clickText("Save")

        flow.waitForText("Namespace renamed.")
        flow.assertTextShown("Flags v2")
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `rename with a blank title leaves the Save button off`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        openMenuItem("Rename namespace")
        flow.typeIntoField("Title", "  ")

        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `delete namespace needs the exact name, then removes the namespace and its keys`() {
        flow.showSettled(KvRoot, KvNamespace(KV_DEMO_NS_FLAGS))
        openMenuItem("Delete namespace")
        flow.waitForText("Delete namespace?")

        compose.onNodeWithText("Delete").assertIsNotEnabled()
        flow.typeIntoField("Type $FLAGS_TITLE to confirm", "Feature")
        compose.onNodeWithText("Delete").assertIsNotEnabled()
        flow.typeIntoField("Type $FLAGS_TITLE to confirm", FLAGS_TITLE)
        flow.clickText("Delete")

        flow.waitUntilTextGone("Delete namespace?")
        flow.waitForText("Session cache")
        flow.waitUntilTextGone(FLAGS_TITLE)
        assertNull(flagsStore().namespaces.get(flagsStore().namespaceKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS)))
        assertEquals(emptySet<String>(), flagsKeyNames())
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `delete namespace cancelled leaves the namespace in place`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        openMenuItem("Delete namespace")
        flow.waitForText("Delete namespace?")

        flow.clickText("Cancel")

        flow.waitUntilTextGone("Delete namespace?")
        flow.assertTextShown(FLAGS_TITLE)
        assertEquals(true, flagsKeyNames().contains(SEEDED_KEY))
    }

    @Test
    fun `the keys list ends with an open-in-dashboard row that opens the web dashboard`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(DASHBOARD_ROW_LIST_INDEX)

        flow.clickText("Open in dashboard")

        assertEquals(listOf("https://dash.cloudflare.com/"), flow.openedUrls)
    }

    @Test
    fun `the metrics tab shows the operations chart and the storage figures`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))

        flow.clickText("Metrics")

        flow.boundsOfDescription("Operations per day for the last 30 days")
        flow.assertTextShown("Storage")
        flow.assertTextShown("Keys on latest day")
        flow.assertTextShown("Bytes on latest day")
    }

    private fun seedExtraKeys() {
        val store = flagsStore()
        (1..EXTRA_KEYS).forEach { index ->
            val name = "bulk/key-%03d".format(index)
            store.entries.put(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, name), KvDemoEntry(name.encodeToByteArray(), null, null))
        }
    }
}
