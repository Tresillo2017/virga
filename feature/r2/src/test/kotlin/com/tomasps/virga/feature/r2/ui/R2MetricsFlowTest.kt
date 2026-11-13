package com.tomasps.virga.feature.r2.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.r2.r2KoinModule
import com.tomasps.virga.feature.r2.registerR2Entries
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DEMO_BUCKET = "demo-assets"
private const val METRICS_TAB = "Metrics"
private const val LAST_TILE_LABEL = "Objects (latest)"
private const val CHART_TITLE = "Requests by operation"

/** The bucket metrics tab, in demo mode. The charts start below the stat tiles, not on top of them. */
@RunWith(RobolectricTestRunner::class)
class R2MetricsFlowTest {

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
    fun `bucket metrics place the operations chart below the stat tiles`() {
        flow.show(R2Bucket(DEMO_BUCKET))

        flow.clickText(METRICS_TAB)
        val lastTile = flow.boundsOf(LAST_TILE_LABEL)
        val chart = flow.boundsOfDescription(CHART_TITLE)

        assertTrue("the chart must start below the last tile: tile=$lastTile chart=$chart", chart.top >= lastTile.bottom)
    }

    @Test
    fun `every time range shows the metrics for that window`() {
        flow.show(R2Bucket(DEMO_BUCKET))
        flow.clickText(METRICS_TAB)

        for (range in listOf("1h", "6h", "24h", "7d", "30d")) {
            flow.clickText(range)
            flow.waitForText("Requests in window")
            flow.assertTextShown(LAST_TILE_LABEL)
        }
    }

    @Test
    fun `the stored-size chart is drawn under the operations chart`() {
        flow.show(R2Bucket(DEMO_BUCKET))
        flow.clickText(METRICS_TAB)

        val operations = flow.boundsOfDescription(CHART_TITLE)

        assertTrue("the operations chart must have a size", operations.height > 0f)
        compose.onNode(hasContentDescription("Stored bytes over time", substring = true)).assertExists()
    }
}
