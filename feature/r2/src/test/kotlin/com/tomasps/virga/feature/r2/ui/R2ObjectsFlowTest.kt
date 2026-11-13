package com.tomasps.virga.feature.r2.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.r2KoinModule
import com.tomasps.virga.feature.r2.registerR2Entries
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ACCOUNT = "demo-account-personal"
private const val ASSETS = "demo-assets"
private const val SEARCH_PLACEHOLDER = "Search by prefix in this folder"
private const val UP_ONE_FOLDER = "Up one folder"

/** The object browser of one bucket, in demo mode: folders, search, details, and deletes. */
@RunWith(RobolectricTestRunner::class)
class R2ObjectsFlowTest {

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
    fun `the bucket root lists its folders first and then its files`() {
        flow.show(R2Bucket(ASSETS))

        flow.assertTextShown("css")
        flow.assertTextShown("img")
        flow.assertTextShown("index.html")
    }

    @Test
    fun `opening a folder lists its files, and Up one folder goes back`() {
        flow.show(R2Bucket(ASSETS))

        flow.clickText("css")
        flow.assertTextShown("site.css")
        flow.assertTextShown("theme.css")
        flow.assertTextShown("css/")

        compose.onNodeWithContentDescription(UP_ONE_FOLDER).performClick()
        flow.waitForText("index.html")
    }

    @Test
    fun `search matches keys in this folder and says so when nothing matches`() {
        flow.show(R2Bucket(ASSETS))

        flow.typeIntoField(SEARCH_PLACEHOLDER, "img/hero")
        flow.waitForText("img/hero.jpg")
        flow.waitUntilTextGone("index.html")

        compose.onNode(hasSetTextAction()).performTextReplacement("no-such-key")
        flow.waitForText("Nothing matches")
    }

    @Test
    fun `an empty bucket says there are no objects`() {
        runBlocking { flow.get<R2Repository>().createBucket(ACCOUNT, "empty-one", null, null) }

        flow.show(R2Bucket("empty-one"))

        flow.waitForText("No objects here")
    }

    @Test
    fun `details show the content type and a readable last-modified date, not a raw timestamp`() {
        flow.show(R2Bucket(ASSETS))

        flow.clickText("index.html")

        flow.waitForText("Content type")
        flow.assertTextShown("text/html")
        flow.assertTextShown("Last modified")
        compose.assertNotShownNow("2026-01-10T09:00:00Z")
        flow.assertTextShown("Storage class")
    }

    @Test
    fun `delete from details asks first, and cancel keeps the object`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText("index.html")
        flow.waitForText("Content type")

        flow.clickText("Delete")
        flow.waitForText("Delete index.html?")
        compose.clickInDialog("Cancel")

        flow.waitUntilTextGone("Delete index.html?")
        flow.assertTextShown("index.html")
    }

    @Test
    fun `confirming delete from details removes the object and reports it`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText("index.html")
        flow.waitForText("Content type")

        flow.clickText("Delete")
        flow.waitForText("Delete index.html?")
        compose.clickInDialog("Delete")

        flow.waitForText("1 deleted.")
        flow.waitUntilTextGone("index.html")
    }

    @Test
    fun `selecting two objects offers a delete of both, and confirming deletes both`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText("css")
        flow.waitForText("site.css")

        compose.clickDescription("Select site.css")
        compose.clickDescription("Select theme.css")
        flow.assertTextShown("2 selected")
        flow.clickText("Delete")
        flow.waitForText("Delete 2 objects?")
        compose.clickInDialog("Delete")

        flow.waitForText("2 deleted.")
        flow.waitUntilTextGone("site.css")
        flow.waitUntilTextGone("theme.css")
    }

    @Test
    fun `cancelling the multi-select delete keeps both objects and the selection`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText("css")
        flow.waitForText("site.css")
        compose.clickDescription("Select site.css")

        flow.clickText("Delete")
        flow.waitForText("Delete site.css?")
        compose.clickInDialog("Cancel")

        flow.waitUntilTextGone("Delete site.css?")
        flow.assertTextShown("1 selected")
        flow.assertTextShown("site.css")
    }

    @Test
    fun `clear drops the selection`() {
        flow.show(R2Bucket(ASSETS))
        flow.clickText("css")
        flow.waitForText("site.css")
        compose.clickDescription("Select site.css")

        flow.clickText("Clear")

        flow.waitUntilTextGone("1 selected")
    }
}
