package com.tomasps.virga.feature.account.signin

import org.junit.Assert.assertFalse
import org.junit.Test

class SignInUiStateTest {

    @Test
    fun `toString never prints the token`() {
        // Arrange
        val state = SignInUiState(token = "cf-secret-canary", isTokenVisible = true)

        // Act
        val printed = state.toString()

        // Assert
        assertFalse(printed.contains("cf-secret-canary"))
    }
}
