package com.tomasps.virga.feature.workers.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.workers.demo.DEMO_SCRIPT_EDGE_API
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val REQUESTS_CHART = "Requests and errors per time bucket"
private const val CPU_CHART = "Highest CPU P50 and P99"

/** The Metrics tab in demo mode: stat tiles, status codes, both charts, and the time range picker. */
@RunWith(RobolectricTestRunner::class)
class WorkerMetricsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = workersFlow(compose)
        flow.show(WorkerDetail(DEMO_SCRIPT_EDGE_API))
        flow.waitForText("Overview")
        flow.clickText("Metrics")
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `metrics show the totals, the status codes and both charts`() {
        flow.waitForText("Requests")
        flow.waitForText("Errors")
        flow.waitForText("Subrequests")
        flow.waitForText("Status codes")
        flow.waitForText("200")
        flow.waitForText("500")
        flow.boundsOfDescription(REQUESTS_CHART)
        flow.boundsOfDescription(CPU_CHART)
    }

    @Test
    fun `the two charts stack without overlapping or leaving a stray chart`() {
        val requests = flow.boundsOfDescription(REQUESTS_CHART)
        val cpu = flow.boundsOfDescription(CPU_CHART)

        assertTrue("CPU chart must start below the requests chart", cpu.top >= requests.bottom)
    }

    @Test
    fun `every time range reloads the charts for that window`() {
        listOf("1h", "6h", "24h", "7d", "30d").forEach { range ->
            flow.clickText(range)

            flow.boundsOfDescription(REQUESTS_CHART)
            flow.boundsOfDescription(CPU_CHART)
            compose.onAllNodesWithText("No invocations in this window.").assertCountEquals(0)
        }
    }
}
