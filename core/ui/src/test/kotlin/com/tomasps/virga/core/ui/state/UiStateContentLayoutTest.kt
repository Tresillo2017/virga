package com.tomasps.virga.core.ui.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.tomasps.virga.core.ui.theme.VirgaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val FIRST_TAG = "first"
private const val SECOND_TAG = "second"
private const val FILL_TAG = "fill"

/** UiStateContent stacks its content vertically, and a single child still gets the whole area. */
@RunWith(RobolectricTestRunner::class)
class UiStateContentLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `several children in the content stack top to bottom instead of overlapping`() {
        showContent {
            Text("first row", modifier = Modifier.testTag(FIRST_TAG))
            Text("second row", modifier = Modifier.testTag(SECOND_TAG))
        }

        val first = compose.onNodeWithTag(FIRST_TAG).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag(SECOND_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the second child must start below the first: $first, $second", second.top >= first.bottom)
    }

    @Test
    fun `a single full-size child fills the state area`() {
        showContent {
            Box(modifier = Modifier.fillMaxSize().testTag(FILL_TAG))
        }

        val area = compose.onRoot().fetchSemanticsNode().size
        val child = compose.onNodeWithTag(FILL_TAG).fetchSemanticsNode().size
        assertEquals(area.width, child.width)
        assertEquals(area.height, child.height)
    }

    private fun showContent(content: @Composable () -> Unit) {
        compose.setContent {
            VirgaTheme(darkTheme = true) {
                UiStateContent(state = UiState.Content(Unit)) { content() }
            }
        }
    }
}
