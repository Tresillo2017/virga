package com.tomasps.virga.feature.r2.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue

/** Taps the node with this content description (icon buttons, checkboxes), then lets Compose settle. */
internal fun ComposeContentTestRule.clickDescription(description: String) {
    onNodeWithContentDescription(description).performClick()
    waitForIdle()
}

/** Taps the button with this text inside a dialog or sheet, so a screen button with the same text is not picked. */
internal fun ComposeContentTestRule.clickInDialog(text: String) {
    onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
    waitForIdle()
}

/** Fails unless some node has exactly this text right now. Use after a tap has settled. */
internal fun ComposeContentTestRule.assertShownNow(text: String) {
    assertTrue("'$text' should be shown", onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
}

/** Fails if some node has exactly this text right now. Use after a tap has settled. */
internal fun ComposeContentTestRule.assertNotShownNow(text: String) {
    assertTrue("'$text' should not be shown", onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
}
