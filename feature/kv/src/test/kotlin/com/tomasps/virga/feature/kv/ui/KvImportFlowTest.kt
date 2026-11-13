package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.kv.KvImport
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val INTRO =
    "Pick a JSON array. Each item needs a key and a value, and may have base64, expiration, expiration_ttl and metadata. " +
        "Up to 10,000 pairs and 100 MB per file. Nothing is sent until you press Import."
private const val IMPORT_TITLE = "Import from JSON"

/**
 * The import screen and the way to it. The system file picker cannot run on the JVM, so picking a file, the preview,
 * the import and the report are covered by unit tests of the view model (KvImportReportTest), not here.
 */
@RunWith(RobolectricTestRunner::class)
class KvImportFlowTest {

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

    @Test
    fun `the namespace menu opens the import screen`() {
        flow.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        compose.onNodeWithContentDescription("More actions").performClick()

        flow.clickText("Import from JSON")

        flow.assertNavigatedTo(KvImport(KV_DEMO_NS_FLAGS))
    }

    @Test
    fun `the import screen explains the format and offers a file, with no preview before a pick`() {
        flow.showSettled(KvImport(KV_DEMO_NS_FLAGS))

        flow.assertTextShown(IMPORT_TITLE)
        flow.assertTextShown(INTRO)
        flow.assertTextShown("Choose file")
        flow.waitUntilTextGone("Preview")
        flow.waitUntilTextGone("Import 0 pairs")
        assertEquals(emptyList<Any>(), flow.history)
    }
}
