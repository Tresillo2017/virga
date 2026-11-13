package com.tomasps.virga.feature.r2.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.navigation.R2Root
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

/**
 * The bucket list, in demo mode. Create-bucket is a dialog with a text field, which Compose cannot idle on under
 * Robolectric (see the guide's known limits); its validation and create logic are covered by R2RootViewModelTest.
 */
@RunWith(RobolectricTestRunner::class)
class R2RootFlowTest {

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
    fun `bucket list shows each seeded bucket with its location and a readable created date`() {
        flow.show(R2Root)

        flow.assertTextShown("demo-assets")
        flow.assertTextShown("demo-backups")
        flow.assertTextShown("demo-logs")
        flow.assertTextShown("West Europe · created Jan 10, 2026")
        flow.assertTextShown("demo-logs")
    }

    @Test
    fun `tapping a bucket opens its screen`() {
        flow.show(R2Root)

        flow.clickText("demo-backups")

        flow.assertNavigatedTo(R2Bucket("demo-backups"))
    }

    @Test
    fun `search narrows the bucket list to matching names`() {
        flow.show(R2Root)

        flow.typeIntoField("Search buckets", "backups")

        flow.waitUntilTextGone("demo-assets")
        flow.assertTextShown("demo-backups")
    }

    @Test
    fun `a search that matches nothing says so and offers to clear it`() {
        flow.show(R2Root)

        flow.typeIntoField("Search buckets", "no-such-bucket")

        flow.waitForText("Nothing matches")
        flow.assertTextShown("No bucket name contains that text. Clear the search to see every bucket.")
    }

    @Test
    fun `coming back from a bucket drops a bucket that was deleted there`() {
        runBlocking { flow.get<R2Repository>().createBucket(ACCOUNT, "scratch-one", null, null) }
        flow.show(R2Root)
        flow.waitForText("scratch-one")
        compose.runOnIdle { flow.navigator.navigate(R2Bucket("scratch-one")) }
        flow.waitForText("Created")
        runBlocking { flow.get<R2Repository>().deleteBucket(ACCOUNT, "scratch-one") }

        compose.runOnIdle { flow.navigator.back() }

        flow.waitUntilTextGone("scratch-one")
    }
}
