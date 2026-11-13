@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.pages.flow

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.pages.pagesKoinModule
import com.tomasps.virga.feature.pages.registerPagesEntries

/** The demo account every Pages flow runs under. Its id is the one the demo session selects first. */
internal const val DEMO_ACCOUNT = "demo-account-personal"
internal const val LANTERN = "lantern-docs"
internal const val TIDEWATER = "tidewater-blog"

/**
 * The Pages feature in demo mode. [extraRoutes] registers test-only stand-ins for screens another feature owns
 * (for example the DNS records screen), so a cross-feature push can be asserted without that feature.
 */
internal fun pagesFlow(
    compose: ComposeContentTestRule,
    extraRoutes: (RouteRegistry) -> Unit = {},
): DemoFlow = DemoFlow(
    compose,
    FeatureUnderTest(listOf(pagesKoinModule)) { registry ->
        registerPagesEntries(registry)
        extraRoutes(registry)
    },
)

/**
 * Scrolls the vertical list that holds [text] until the node exists, so an off-screen row can be tapped or read.
 * Tries the innermost vertical scrollable first. Tab rows scroll sideways and are skipped.
 */
internal fun ComposeContentTestRule.scrollToText(text: String, substring: Boolean = false) {
    val target = hasText(text, substring = substring)
    if (onAllNodes(target).fetchSemanticsNodes().isNotEmpty()) return
    val lists = onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
    val count = lists.fetchSemanticsNodes().size
    for (index in count - 1 downTo 0) {
        val scrolled = runCatching { lists[index].performScrollToNode(target) }
        if (scrolled.isSuccess) return
    }
    throw AssertionError("No vertical list shows \"$text\"")
}

/** Taps the [index]th clickable element showing [text], scrolling it into view first. */
internal fun ComposeContentTestRule.tapText(text: String, index: Int = 0) {
    scrollToText(text)
    waitUntilAtLeastOneExists(hasText(text), FLOW_WAIT_TIMEOUT_MS)
    onAllNodes(hasText(text) and hasClickAction())[index].performClick()
    waitForIdle()
}

/** Taps the clickable element showing [text] inside a dialog. Dialogs sit in their own window, so this avoids the screen behind. */
internal fun ComposeContentTestRule.tapInDialog(text: String) {
    val matcher = hasText(text) and hasClickAction() and inDialog()
    waitUntilAtLeastOneExists(matcher, FLOW_WAIT_TIMEOUT_MS)
    onNode(matcher).performClick()
    waitForIdle()
}

/** The dialog button showing [text], for enabled-state assertions. */
internal fun ComposeContentTestRule.dialogButton(text: String): SemanticsNodeInteraction =
    onNode(hasText(text) and hasClickAction() and inDialog())

/** Types into the [index]th editable on screen, replacing its text. */
internal fun ComposeContentTestRule.typeIntoEditable(index: Int, text: String) {
    onAllNodes(hasSetTextAction(), useUnmergedTree = true)[index].performTextReplacement(text)
    waitForIdle()
}

/** Presses the keyboard's Done key on the [index]th editable on screen. */
internal fun ComposeContentTestRule.pressDoneOnEditable(index: Int) {
    onAllNodes(hasSetTextAction(), useUnmergedTree = true)[index].performImeAction()
    waitForIdle()
}

/** Types into the single editable inside the open dialog (the typed-name confirmation). No idle wait, see [tapText]. */
internal fun ComposeContentTestRule.typeIntoDialogField(text: String) {
    onNode(hasSetTextAction() and inDialog(), useUnmergedTree = true).performTextReplacement(text)
    waitForIdle()
}

/**
 * Dialogs that hold a text field cannot be driven in this harness: Compose never goes idle while one is open under
 * Robolectric, so every wait after a tap or typed input times out. Those tests stay in the suite, marked ignored,
 * and their validation logic is covered by unit tests. See docs/feature-module-guide.md "UI flow tests" for limits.
 */
internal const val TEXT_FIELD_DIALOG_BLOCKED = "Compose never goes idle with a text field in an AlertDialog under Robolectric"

/** Types into the first editable of the open dialog: the name in the variable form. No idle wait, see [tapText]. */
internal fun ComposeContentTestRule.typeIntoDialogFirst(text: String) {
    onAllNodes(hasSetTextAction() and inDialog(), useUnmergedTree = true).onFirst().performTextReplacement(text)
    waitForIdle()
}

/** Types into the last editable of the open dialog: the value in the variable form. No idle wait, see [tapText]. */
internal fun ComposeContentTestRule.typeIntoDialogLast(text: String) {
    onAllNodes(hasSetTextAction() and inDialog(), useUnmergedTree = true).onLast().performTextReplacement(text)
    waitForIdle()
}

/** A dialog's title or message is shown. */
internal fun ComposeContentTestRule.assertDialogText(text: String) {
    waitUntilAtLeastOneExists(hasText(text) and inDialog(), FLOW_WAIT_TIMEOUT_MS)
}

/** No dialog is open with [text]. */
internal fun ComposeContentTestRule.assertNoDialogText(text: String) {
    onAllNodes(hasText(text) and inDialog()).assertCountEquals(0)
}

private fun inDialog(): SemanticsMatcher = hasAnyAncestor(isDialog())
