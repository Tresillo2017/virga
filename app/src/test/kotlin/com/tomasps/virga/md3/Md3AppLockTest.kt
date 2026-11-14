package com.tomasps.virga.md3

import androidx.compose.material3.Text
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tomasps.virga.shell.AppLockGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SECRET = "account content"

/** Material 3 audit, app lock setting effect: the gate hides the app until the user is verified. */
@RunWith(RobolectricTestRunner::class)
class Md3AppLockTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shown(text: String): Boolean = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a locked app hides its content and offers to unlock`() {
        var prompts = 0
        compose.setContent { AppLockGate(enabled = true, authenticate = { prompts++ }) { Text(SECRET) } }
        compose.waitForIdle()

        assertTrue("content visible while locked", !shown(SECRET))
        compose.onNode(hasText("Unlock") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertTrue("unlock did not start the prompt", prompts >= 1)
        assertTrue("content visible before verification", !shown(SECRET))
    }

    @Test
    fun `a verified user sees the content`() {
        compose.setContent { AppLockGate(enabled = true, authenticate = { onSuccess -> onSuccess() }) { Text(SECRET) } }
        compose.waitForIdle()

        if (!shown(SECRET)) compose.onNode(hasText("Unlock") and hasClickAction()).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(SECRET).assertExists()
    }

    @Test
    fun `with app lock off the content shows without a prompt`() {
        var prompts = 0
        compose.setContent { AppLockGate(enabled = false, authenticate = { prompts++ }) { Text(SECRET) } }
        compose.waitForIdle()

        compose.onNodeWithText(SECRET).assertExists()
        assertEquals(0, prompts)
        assertTrue(!shown("Unlock"))
    }
}
