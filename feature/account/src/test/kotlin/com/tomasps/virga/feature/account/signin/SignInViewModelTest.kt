package com.tomasps.virga.feature.account.signin

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.feature.account.testing.FakeSessionRepository
import com.tomasps.virga.feature.account.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignInViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `an empty token shows an error and never reaches the session`() {
        // Arrange
        val session = FakeSessionRepository()
        val viewModel = SignInViewModel(session)

        // Act
        viewModel.onSignIn()

        // Assert
        assertTrue(viewModel.state.value.error != null)
        assertTrue(session.signInTokens.isEmpty())
    }

    @Test
    fun `a token is trimmed, verified once, and cleared from the field on success`() {
        // Arrange
        val session = FakeSessionRepository()
        val viewModel = SignInViewModel(session)
        viewModel.onTokenChanged("  cf-token-abc  ")

        // Act
        viewModel.onSignIn()

        // Assert
        assertEquals(listOf("cf-token-abc"), session.signInTokens)
        assertEquals("", viewModel.state.value.token)
        assertFalse(viewModel.state.value.isVerifying)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a rejected token keeps the field and shows the mapped error`() {
        // Arrange
        val session = FakeSessionRepository().apply {
            signInResult = ApiResult.Failure(ApiError.Unauthorized())
        }
        val viewModel = SignInViewModel(session)
        viewModel.onTokenChanged("cf-bad")

        // Act
        viewModel.onSignIn()

        // Assert
        assertEquals("cf-bad", viewModel.state.value.token)
        assertEquals(ApiErrorUiMapper.toErrorUi(ApiError.Unauthorized()), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isVerifying)
    }

    @Test
    fun `typing clears the previous error`() {
        // Arrange
        val viewModel = SignInViewModel(FakeSessionRepository())
        viewModel.onSignIn()

        // Act
        viewModel.onTokenChanged("x")

        // Assert
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `explore the demo enters demo mode through the session`() {
        // Arrange
        val session = FakeSessionRepository()
        val viewModel = SignInViewModel(session)

        // Act
        viewModel.onExploreDemo()

        // Assert
        assertEquals(1, session.demoEntries)
        assertFalse(viewModel.state.value.isVerifying)
    }

    @Test
    fun `visibility and guidance toggles flip their flags`() {
        // Arrange
        val viewModel = SignInViewModel(FakeSessionRepository())

        // Act
        viewModel.onToggleTokenVisibility()
        viewModel.onGuidanceToggled()

        // Assert
        assertTrue(viewModel.state.value.isTokenVisible)
        assertTrue(viewModel.state.value.isGuidanceExpanded)
    }
}
