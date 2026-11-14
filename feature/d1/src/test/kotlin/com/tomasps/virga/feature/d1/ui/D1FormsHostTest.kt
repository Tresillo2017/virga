@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.d1.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.collectAsState
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.core.ui.state.UiState
import org.koin.core.parameter.parametersOf
import com.tomasps.virga.core.ui.theme.VirgaTheme
import com.tomasps.virga.feature.d1.d1KoinModule
import com.tomasps.virga.feature.d1.detail.tables.RowEditorFields
import com.tomasps.virga.feature.d1.detail.tables.TablesViewModel
import com.tomasps.virga.feature.d1.list.CreateDatabaseFields
import com.tomasps.virga.feature.d1.list.D1ListViewModel
import com.tomasps.virga.feature.d1.registerD1Entries
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

private const val WAIT_MS = 10_000L
private const val POLL_MILLIS = 10L
private const val CUSTOMERS = "customers"

/**
 * The create-database form and the row editor, rendered without their dialog window. A text field inside a dialog
 * never settles under Robolectric, so these forms are driven as plain content, with the real view models behind them
 * and the demo graph underneath. The dialog shells only add a title and buttons; they are not tested here.
 */
@RunWith(RobolectricTestRunner::class)
class D1FormsHostTest {

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

    // Create database

    @Test
    fun `the create form shows readable jurisdiction and location choices, and keeps each choice`() {
        val list = flow.koin.get<D1ListViewModel>()
        list.openCreate()
        compose.setContent { CreateHost(list) }

        flow.clickText("EU")
        flow.clickText("Western Europe")

        flow.assertTextShown("Western Europe")
        val draft = list.state.value.create
        assertEquals("eu", draft?.jurisdiction)
        assertEquals("weur", draft?.locationHint)
        compose.onAllNodesWithText("weur").assertCountEquals(0)
    }

    @Test
    fun `submitting an empty name shows the name error and creates nothing`() {
        val list = flow.koin.get<D1ListViewModel>()
        list.openCreate()
        compose.setContent { CreateHost(list) }

        flow.clickText("Submit")

        flow.waitForText("Enter a name for the database.")
        assertEquals(2, listedDatabases(flow.get()).size)
    }

    @Test
    fun `a typed name is created, field after field, and the list reloads with it`() {
        val list = flow.koin.get<D1ListViewModel>()
        list.openCreate()
        compose.setContent { CreateHost(list) }

        flow.typeIntoField("Name", "fresh-db")
        flow.clickText("EU")
        flow.clickText("Submit")

        flow.waitUntil(WAIT_MS) { list.state.value.create == null }
        val created = listedDatabases(flow.get()).single { it.name == "fresh-db" }
        assertEquals("eu", created.jurisdiction)
    }

    // Row editor

    @Test
    fun `typing into each insert field keeps every value, and review binds them in column order`() {
        val tables = openTable(CUSTOMERS)
        tables.beginInsert()
        compose.setContent { RowEditorHost(tables) }

        flow.typeIntoField("name", "Dana Okafor")
        flow.typeIntoField("id", "4")
        flow.typeIntoField("region", "EU")

        val editor = tables.state.value.editor
        assertEquals(listOf("4", "Dana Okafor", "EU"), listOf("id", "name", "region").map { column -> editor?.cells?.first { it.column == column }?.text })
        tables.reviewEditor()
        val pending = tables.state.value.pendingWrite?.statement
        assertEquals(listOf("4", "Dana Okafor", "EU"), pending?.params)
        assertEquals("INSERT INTO \"customers\" (\"id\", \"name\", \"region\") VALUES (?, ?, ?)", pending?.sql)
    }

    @Test
    fun `a blank insert field is left out, so the column takes its default`() {
        val tables = openTable(CUSTOMERS)
        tables.beginInsert()
        compose.setContent { RowEditorHost(tables) }

        flow.typeIntoField("name", "Eli")
        tables.reviewEditor()

        assertEquals("INSERT INTO \"customers\" (\"name\") VALUES (?)", tables.state.value.pendingWrite?.statement?.sql)
    }

    @Test
    fun `a NULL is written as the keyword, and a text value is bound exactly as typed`() {
        val tables = openTable(CUSTOMERS)
        tables.beginInsert()
        compose.setContent { RowEditorHost(tables) }
        val tricky = "O'Brien, \"Ann\" NULL"

        flow.typeIntoField("name", tricky)
        compose.onAllNodesWithText("Set NULL").get(REGION_CHIP).performClick()
        tables.reviewEditor()

        val pending = tables.state.value.pendingWrite?.statement
        assertEquals("INSERT INTO \"customers\" (\"name\", \"region\") VALUES (?, NULL)", pending?.sql)
        assertEquals(listOf(tricky), pending?.params)
    }

    @Test
    fun `an edit changes only the field that was typed into, keyed by the primary key`() {
        val tables = openTable(CUSTOMERS)
        tables.beginEdit(0)
        compose.setContent { RowEditorHost(tables) }

        flow.typeIntoField("name", "Ada L.")
        tables.reviewEditor()

        val pending = tables.state.value.pendingWrite?.statement
        assertEquals("UPDATE \"customers\" SET \"name\" = ? WHERE \"id\" = ?", pending?.sql)
        assertEquals(listOf("Ada L.", "1"), pending?.params)
    }

    private fun openTable(table: String): TablesViewModel {
        val tables = flow.koin.get<TablesViewModel> { parametersOf(ORDERS_ID) }
        tables.openTable(table)
        awaitState { tables.state.value.rows is UiState.Content }
        return tables
    }

    /**
     * Waits for a view model to reach [condition]. Its work runs on the paused main looper, which this drains between
     * checks. Used before any compose content is set, when a compose wait would not drive that looper.
     */
    private fun awaitState(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "The view model did not reach the expected state" }
            ShadowLooper.idleMainLooper()
            Thread.sleep(POLL_MILLIS)
        }
    }

    @Composable
    private fun CreateHost(list: D1ListViewModel) {
        val state by list.state.collectAsState()
        VirgaTheme(darkTheme = true) {
            val draft = state.create
            if (draft != null) {
                CreateDatabaseFields(
                    name = draft.name,
                    nameError = draft.nameError,
                    jurisdiction = draft.jurisdiction,
                    locationHint = draft.locationHint,
                    error = draft.error,
                    onNameChanged = list::onNameChanged,
                    onJurisdictionChanged = list::onJurisdictionChanged,
                    onLocationChanged = list::onLocationChanged,
                )
            }
            Button(onClick = list::submitCreate) { Text("Submit") }
        }
    }

    @Composable
    private fun RowEditorHost(tables: TablesViewModel) {
        val state by tables.state.collectAsState()
        VirgaTheme(darkTheme = true) {
            state.editor?.let { editor ->
                RowEditorFields(editor = editor, onCellText = tables::onCellText, onCellNull = tables::onCellNull)
            }
        }
    }

    private companion object {
        /** The region column is the third, so its NULL chip is the third "Set NULL" on an insert. */
        const val REGION_CHIP = 2
    }
}
