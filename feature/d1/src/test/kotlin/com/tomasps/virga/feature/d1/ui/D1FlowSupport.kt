@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.d1.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.d1.data.D1Database
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import kotlinx.coroutines.runBlocking

/** The demo account the flows run under. Its databases are seeded in D1DemoRoutes. */
const val D1_DEMO_ACCOUNT = "demo-account-personal"
const val ORDERS_ID = "demo-db-orders"
const val ORDERS_NAME = "orders-demo"
const val LEDGER_NAME = "ledger-demo"

/** A text field inside a dialog, found by its visible label. Dialog labels can repeat grid headers behind the dialog. */
fun ComposeContentTestRule.dialogField(label: String): SemanticsNodeInteraction = onNode(
    hasSetTextAction() and hasAnyAncestor(isDialog()) and hasAnyDescendant(hasText(label)),
    useUnmergedTree = true,
)

/** The database list as the repository reads it back, so a flow asserts what was stored, not what was shown. */
fun listedDatabases(repository: DatabasesRepository): List<D1Database> = runBlocking {
    when (val result = repository.list(D1_DEMO_ACCOUNT, page = 1)) {
        is ApiResult.Success -> result.value
        is ApiResult.Failure -> throw AssertionError("Listing failed: ${result.error}")
    }
}
