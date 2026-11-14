package com.tomasps.virga.shell

import androidx.compose.material3.Text
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val CONTENT = "account content"

@RunWith(RobolectricTestRunner::class)
class AppLockGateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an unlocked app stays unlocked across recreation without a second prompt`() {
        var prompts = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AppLockGate(enabled = true, authenticate = { onSuccess -> prompts++; onSuccess() }) { Text(CONTENT) }
        }
        compose.onNodeWithText(CONTENT).assertExists()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(CONTENT).assertExists()
        assertEquals(1, prompts)
    }

    @Test
    fun `locked content is not exposed to accessibility services`() {
        compose.setContent { AppLockGate(enabled = true, authenticate = { }) { Text(CONTENT) } }
        compose.waitForIdle()

        // The merged tree is what accessibility services read.
        assertTrue(compose.onAllNodes(hasText(CONTENT)).fetchSemanticsNodes().isEmpty())
    }
}
