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

private const val BACKUPS = "demo-backups"

/** One bucket's screen: the header facts, the three tabs and the overflow menu, in demo mode. */
@RunWith(RobolectricTestRunner::class)
class R2BucketFlowTest {

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
    fun `the header shows the location, storage class and a readable created date`() {
        flow.show(R2Bucket(BACKUPS))

        flow.assertTextShown("Location")
        flow.assertTextShown("West Europe")
        flow.assertTextShown("Default storage class")
        flow.assertTextShown("Infrequent Access")
        flow.assertTextShown("Created")
        flow.assertTextShown("Jan 10, 2026")
    }

    @Test
    fun `the three tabs are shown and the objects tab is the first`() {
        flow.show(R2Bucket(BACKUPS))

        flow.assertTextShown("Objects")
        flow.assertTextShown("Settings")
        flow.assertTextShown("Metrics")
        flow.waitForText("2026-10")
    }

    @Test
    fun `tapping Settings and Metrics switches the tab content`() {
        flow.show(R2Bucket(BACKUPS))

        flow.clickText("Settings")
        flow.waitForText("Bucket lock")
        flow.clickText("Metrics")

        flow.waitForText("Requests in window")
    }

    @Test
    fun `the overflow menu offers delete bucket`() {
        flow.show(R2Bucket(BACKUPS))

        compose.onNodeWithContentDescription("More actions").performClick()

        flow.waitForText("Delete bucket")
    }
}
