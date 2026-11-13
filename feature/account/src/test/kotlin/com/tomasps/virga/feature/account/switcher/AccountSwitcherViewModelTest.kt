package com.tomasps.virga.feature.account.switcher

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.feature.account.testing.FakeSessionRepository
import com.tomasps.virga.feature.account.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountSwitcherViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val accounts = listOf(CloudflareAccount("a1", "Alpha"), CloudflareAccount("a2", "Beta"))

    @Test
    fun `rows list every account and mark only the selected one`() {
        // Arrange
        val session = FakeSessionRepository(SessionState.SignedIn(accounts, "a2", isDemo = false))

        // Act
        val rows = AccountSwitcherViewModel(session).state.value.rows

        // Assert
        assertEquals(listOf("a1", "a2"), rows.map { it.id })
        assertEquals(listOf(false, true), rows.map { it.isSelected })
    }

    @Test
    fun `selecting an account persists it through the session and then reports done`() {
        // Arrange
        val session = FakeSessionRepository(SessionState.SignedIn(accounts, "a1", isDemo = false))
        val viewModel = AccountSwitcherViewModel(session)
        var done = false

        // Act
        viewModel.onAccountSelected("a2") { done = true }

        // Assert
        assertEquals(listOf("a2"), session.selectedAccountIds)
        assertTrue(done)
    }
}
