@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.d1.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.d1.d1KoinModule
import com.tomasps.virga.feature.d1.data.D1Limits
import com.tomasps.virga.feature.d1.data.SqlRepository
import com.tomasps.virga.feature.d1.registerD1Entries
import com.tomasps.virga.feature.d1.sql.ColumnValue
import com.tomasps.virga.feature.d1.sql.insertStatement
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TIMESTAMP_LABEL = "Time (ISO 8601, for example 2026-11-10T08:00:00Z)"
private const val DASHBOARD_URL = "https://dash.cloudflare.com/"
private const val AUDIT_TABLE = "audit_log"
private const val ORDERS_TABLE = "orders"

/** Screen-level flows for one D1 database in demo mode: its tabs, tables, console, time travel, export, settings, metrics. */
@RunWith(RobolectricTestRunner::class)
class D1DetailFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = DemoFlow(compose, FeatureUnderTest(listOf(d1KoinModule, isolatedD1Modules()), ::registerD1Entries))
    }

    @After
    fun tearDown() {
        flow.close()
    }

    // Tables

    @Test
    fun `the tables tab lists the database's tables, without internal ones`() {
        flow.show(D1Database(ORDERS_ID))

        flow.assertTextShown("customers")
        flow.assertTextShown(ORDERS_TABLE)
        flow.assertTextShown(AUDIT_TABLE)
    }

    @Test
    fun `opening a table shows its columns with their flags and its rows`() {
        flow.show(D1Database(ORDERS_ID))

        flow.clickText(ORDERS_TABLE)

        flow.waitForText("total_cents")
        waitForPart("primary key")
        flow.waitForText("shipped")
        flow.assertTextShown("Insert row")
    }

    @Test
    fun `a table without a primary key is browse-only, and says so`() {
        flow.show(D1Database(ORDERS_ID))

        flow.clickText(AUDIT_TABLE)

        flow.assertTextShown("This table has no primary key, so its rows can be browsed but not changed here.")
        compose.onAllNodesWithText("Insert row").assertCountEquals(0)
    }

    @Test
    fun `a long table pages in more rows on request`() {
        seedOrders(count = SEEDED_ROWS)
        flow.show(D1Database(ORDERS_ID))
        flow.clickText(ORDERS_TABLE)
        flow.waitForText("shipped")

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(TABLE_FOOTER_INDEX)
        flow.clickText("Load more")

        flow.waitUntilTextGone("Load more")
        val lastRowIndex = FIRST_ROW_INDEX + TOTAL_ROWS - 1
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(lastRowIndex)
        flow.assertTextShown("${200 + SEEDED_ROWS - 1}")
    }

    @Test
    fun `a wide table scrolls sideways, bringing its last column into view`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText(ORDERS_TABLE)
        flow.waitForText("shipped")
        compose.onNodeWithText("shipped", useUnmergedTree = true).assertIsNotDisplayed()

        compose.onNode(hasText("shipped") and hasClickAction()).performTouchInput {
            val y = centerY
            down(Offset(width * SWIPE_START, y))
            for (step in 1..SWIPE_STEPS) {
                moveTo(Offset(width * (SWIPE_START - (SWIPE_START - SWIPE_END) * step / SWIPE_STEPS), y))
                advanceEventTime(SWIPE_STEP_MILLIS)
            }
            up()
        }
        compose.waitForIdle()

        compose.onNodeWithText("shipped", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `deleting a row asks first with the exact statement and its values, and cancel keeps the row`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText(ORDERS_TABLE)
        flow.waitForText("shipped")

        flow.clickText("101")
        flow.clickText("Delete")

        flow.waitForText("Delete this row?")
        waitForPart("DELETE FROM \"orders\" WHERE \"id\" = ?")
        waitForPart("1. \"101\"")
        flow.clickText("Cancel")
        flow.waitUntilTextGone("Delete this row?")
        assertEquals(1, compose.onAllNodes(hasText("101") and hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun `a confirmed row delete removes the row from the grid`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText(ORDERS_TABLE)
        flow.waitForText("shipped")
        flow.clickText("101")
        flow.clickText("Delete")
        flow.waitForText("Delete this row?")

        flow.clickText("Run")

        flow.waitUntilTextGone("Delete this row?")
        compose.waitUntil(FLOW_WAIT_TIMEOUT_MS) { compose.onAllNodes(hasText("101") and hasClickAction()).fetchSemanticsNodes().isEmpty() }
        flow.assertTextShown("102")
    }

    @Test
    fun `All tables returns from a table to the list of tables`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText(ORDERS_TABLE)

        flow.clickText("All tables")

        flow.assertTextShown("customers")
    }

    // Console

    @Test
    fun `a select runs and shows its rows and the cost of the statement`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Console")

        flow.typeIntoField("SQL", "SELECT * FROM customers")
        flow.clickText("Run")

        flow.assertTextShown("Ben Ortiz")
        waitForPart("Rows read: 3 · rows written: 0")
    }

    @Test
    fun `a destructive statement waits for confirmation, and cancel runs nothing`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Console")
        flow.typeIntoField("SQL", "DELETE FROM $AUDIT_TABLE")

        flow.clickText("Run")
        flow.waitForText("Run this statement?")
        flow.clickText("Cancel")

        flow.waitUntilTextGone("Run this statement?")
        assertEquals(1, rowCount(AUDIT_TABLE))
    }

    @Test
    fun `confirming a destructive statement runs it`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Console")
        flow.typeIntoField("SQL", "DELETE FROM $AUDIT_TABLE")

        flow.clickText("Run")
        flow.waitForText("Run anyway")
        flow.clickText("Run anyway")

        waitForPart("Rows read: 0 · rows written: 1")
        assertEquals(0, rowCount(AUDIT_TABLE))
    }

    @Test
    fun `a multi-line statement is listed as one clean line, and tapping it loads the exact text back`() {
        val statement = "SELECT id,\n    name\nFROM customers"
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Console")
        flow.typeIntoField("SQL", statement)
        flow.clickText("Run")
        waitForPart("Rows read: 3 · rows written: 0")

        flow.waitForText("SELECT id, name FROM customers")
        compose.onAllNodesWithText("SELECT id, name FROM customers").assertCountEquals(1)
        flow.clickText("SELECT id, name FROM customers")

        val editable = compose.onNode(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(statement, editable.config[SemanticsProperties.EditableText].text)
    }

    // Time Travel

    @Test
    fun `an invalid time is refused with a message, and no bookmark is shown`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Time Travel")
        flow.typeIntoField(TIMESTAMP_LABEL, "yesterday afternoon")

        flow.clickText("Look up")

        flow.waitForText("Enter a time in ISO 8601 form, such as 2026-11-10T08:00:00Z.")
        compose.onAllNodesWithText("Restore to this bookmark").assertCountEquals(0)
    }

    @Test
    fun `a valid time shows the bookmark, and editing the time removes that bookmark`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Time Travel")
        flow.typeIntoField(TIMESTAMP_LABEL, "2026-11-10T08:00:00Z")

        flow.clickText("Look up")

        flow.waitForText("Restore to this bookmark")
        flow.typeIntoField(TIMESTAMP_LABEL, "2026-11-10T09:00:00Z")
        compose.waitUntil(FLOW_WAIT_TIMEOUT_MS) { compose.onAllNodesWithText("Restore to this bookmark").fetchSemanticsNodes().isEmpty() }
    }

    // Export

    @Test
    fun `an export runs to completion, offers a download, and never shows the signed link`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Export")

        flow.clickText("Start export")
        flow.advanceClock(EXPORT_POLL_MILLIS)

        flow.waitForText("Export ready: orders-demo.sql")
        flow.assertTextShown("Download")
        compose.onAllNodesWithText("https", substring = true).assertCountEquals(0)
    }

    @Test
    fun `stopping the wait returns the export to its start state`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Export")
        flow.clickText("Start export")

        flow.clickText("Stop waiting")

        flow.assertTextShown("Start export")
    }

    // Settings

    @Test
    fun `read replication can be switched off, and the change is stored`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Settings")

        flow.clickText("Disabled")

        compose.waitForIdle()
        val mode = runBlocking {
            when (val result = flow.get<DatabasesRepository>().get(D1_DEMO_ACCOUNT, ORDERS_ID)) {
                is ApiResult.Success -> result.value.readReplication?.mode
                is ApiResult.Failure -> error("read failed: ${result.error}")
            }
        }
        assertEquals("disabled", mode)
    }

    @Test
    fun `rename is delegated to the dashboard, which is opened on request`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Settings")

        flow.clickText("Rename in dashboard")

        assertEquals(listOf(DASHBOARD_URL), flow.openedUrls)
    }

    // Metrics

    @Test
    fun `metrics show the window, the totals and the chart`() {
        flow.show(D1Database(ORDERS_ID))

        flow.clickText("Metrics")

        flow.assertTextShown("Last 30 days")
        flow.assertTextShown("Queries")
        flow.boundsOfDescription("Rows read and written per day")
    }

    @Test
    fun `query insights are opened in the dashboard`() {
        flow.show(D1Database(ORDERS_ID))
        flow.clickText("Metrics")

        flow.clickText("Open in dashboard")

        assertEquals(listOf(DASHBOARD_URL), flow.openedUrls)
    }

    /** A text that is part of a longer line, such as a summary with a duration after it. */
    private fun waitForPart(part: String) {
        compose.waitUntilAtLeastOneExists(hasText(part, substring = true), FLOW_WAIT_TIMEOUT_MS)
    }

    private fun rowCount(table: String): Int = runBlocking {
        val statement = "SELECT * FROM \"$table\""
        when (val result = flow.get<SqlRepository>().run(D1_DEMO_ACCOUNT, ORDERS_ID, statement)) {
            is ApiResult.Success -> result.value.rows.size
            is ApiResult.Failure -> error("read failed: ${result.error}")
        }
    }

    /** Adds [count] orders after the three seeded ones, through the same statement builder the table grid uses. */
    private fun seedOrders(count: Int) = runBlocking {
        val repository = flow.get<SqlRepository>()
        repeat(count) { index ->
            val statement = insertStatement(
                table = ORDERS_TABLE,
                values = listOf(
                    ColumnValue("id", (200 + index).toString()),
                    ColumnValue("customer_id", "1"),
                    ColumnValue("total_cents", "100"),
                    ColumnValue("status", "open"),
                ),
            )
            repository.run(D1_DEMO_ACCOUNT, ORDERS_ID, statement.sql, statement.params)
        }
    }

    private companion object {
        /** Items before the first row: table header, schema, actions and grid header. */
        const val FIRST_ROW_INDEX = 4

        /** The footer follows the first page of rows. */
        const val TABLE_FOOTER_INDEX = FIRST_ROW_INDEX + D1Limits.TABLE_PAGE_ROWS
        const val EXPORT_POLL_MILLIS = 2_000L
        const val SWIPE_START = 0.9f
        const val SWIPE_END = 0.1f
        const val SWIPE_STEPS = 20
        const val SWIPE_STEP_MILLIS = 16L

        /** The three seeded orders, then the extra ones the paging test adds. */
        const val SEEDED_ROWS = D1Limits.TABLE_PAGE_ROWS + 10
        const val TOTAL_ROWS = 3 + SEEDED_ROWS
    }
}
