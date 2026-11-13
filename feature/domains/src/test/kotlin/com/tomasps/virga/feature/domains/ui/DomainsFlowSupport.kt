@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.domains.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.domains.domainsKoinModule
import com.tomasps.virga.feature.domains.registerDomainsEntries
import com.tomasps.virga.feature.domains.demo.DEMO_ZONE_EXAMPLE_COM
import com.tomasps.virga.feature.domains.demo.DEMO_ZONE_EXAMPLE_NET
import com.tomasps.virga.feature.domains.demo.DEMO_ZONE_EXAMPLE_ORG

/** The demo zone ids, named as the demo data names them. */
internal const val ZONE_COM = DEMO_ZONE_EXAMPLE_COM
internal const val ZONE_ORG = DEMO_ZONE_EXAMPLE_ORG
internal const val ZONE_NET = DEMO_ZONE_EXAMPLE_NET

/**
 * A demo flow for the Domains feature. WorkerDetail belongs to another feature, so it is stubbed here: a test
 * only records that the link was followed.
 */
internal fun domainsFlow(compose: ComposeContentTestRule, darkTheme: Boolean = true): DemoFlow = DemoFlow(
    compose,
    FeatureUnderTest(listOf(domainsKoinModule), ::registerDomainsWithWorkerStub),
    darkTheme = darkTheme,
)

private fun registerDomainsWithWorkerStub(routes: RouteRegistry) {
    registerDomainsEntries(routes)
    routes.route(WorkerDetail.serializer()) { Text("Worker stub") }
}

/** Taps the node with this content description (icon buttons, switches). Fails when none or several match. */
internal fun ComposeContentTestRule.tapDescription(description: String) {
    onNodeWithContentDescription(description).performClick()
    waitForIdle()
}

/** Taps the node with this test tag. */
internal fun ComposeContentTestRule.tapTag(tag: String) {
    onNode(hasTestTag(tag), useUnmergedTree = true).performClick()
    waitForIdle()
}

/** How many nodes show exactly [text]. Repeated rows (several records with one name) are counted this way. */
internal fun ComposeContentTestRule.countText(text: String): Int =
    onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

/** Types into the text field that shows [placeholder] while empty (a search field has no label). */
internal fun ComposeContentTestRule.typeIntoPlaceholder(placeholder: String, text: String) {
    onNode(hasSetTextAction() and hasAnyDescendant(hasText(placeholder)), useUnmergedTree = true)
        .performTextReplacement(text)
    waitForIdle()
}

/** How many text fields currently hold [text]. Used to check that a form was cleared. */
internal fun ComposeContentTestRule.countFieldsHolding(text: String): Int =
    onAllNodes(hasSetTextAction() and hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size

/** Scrolls the lazy list or column until a node with exactly [text] is composed. */
internal fun ComposeContentTestRule.scrollToText(text: String) {
    onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    waitForIdle()
}

/** Fails when any node shows exactly [text]. Used for raw values that must not reach the user. */
internal fun ComposeContentTestRule.assertNotShownText(text: String) {
    onAllNodesWithText(text, useUnmergedTree = true).assertCountEquals(0)
}

/** Waits for, then asserts, a node whose text contains [fragment]. For values with a variable part, such as a countdown. */
internal fun ComposeContentTestRule.assertTextContaining(fragment: String) {
    waitUntilAtLeastOneExists(hasText(fragment, substring = true))
    onNode(hasText(fragment, substring = true), useUnmergedTree = true).assertIsDisplayed()
}

/**
 * Left edge, in pixels, of the text node that shows exactly [text]. The unmerged node is the text itself, so the value
 * is where the words start, not the padded edge of the row that holds them.
 */
internal fun ComposeContentTestRule.leftEdgeOf(text: String): Float {
    waitUntil(FLOW_WAIT_TIMEOUT_MS) { onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    return onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
}

/** Taps the text inside the element tagged [tag]. Picks a choice within one part of a screen, such as a type in the editor. */
internal fun ComposeContentTestRule.tapTextInTag(tag: String, text: String) {
    onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
        .performScrollTo()
        .performClick()
    waitForIdle()
}
