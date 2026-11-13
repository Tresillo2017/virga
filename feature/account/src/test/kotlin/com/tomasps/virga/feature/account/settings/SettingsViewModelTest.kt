package com.tomasps.virga.feature.account.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.feature.account.testing.FakeSessionRepository
import com.tomasps.virga.feature.account.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val TIMEOUT_MS = 5_000L

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private fun themes(): ThemeRepository {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ) { File(folder.root, "vm-settings.preferences_pb") }
        return ThemeRepository(store)
    }

    @Test
    fun `choosing a theme updates the state and persists it`() = runBlocking {
        // Arrange
        val themes = themes()
        val viewModel = SettingsViewModel(FakeSessionRepository(), themes)

        // Act
        viewModel.onThemeSelected(ThemeMode.Light)

        // Assert
        val observed = withTimeout(TIMEOUT_MS) { viewModel.state.first { it.themeMode == ThemeMode.Light } }
        assertEquals(ThemeMode.Light, observed.themeMode)
        assertEquals(ThemeMode.Light, themes.mode.first())
    }

    @Test
    fun `sign out asks first and does nothing when dismissed`() {
        // Arrange
        val session = FakeSessionRepository(SessionState.SignedOut)
        val viewModel = SettingsViewModel(session, themes())

        // Act
        viewModel.onSignOutRequested()
        val askedFirst = viewModel.state.value.isSignOutConfirmVisible
        viewModel.onSignOutDismissed()

        // Assert
        assertTrue(askedFirst)
        assertFalse(viewModel.state.value.isSignOutConfirmVisible)
        assertEquals(0, session.signOuts)
    }

    @Test
    fun `confirming sign out wipes the session and hides the dialog`() {
        // Arrange
        val session = FakeSessionRepository(SessionState.SignedOut)
        val viewModel = SettingsViewModel(session, themes())
        viewModel.onSignOutRequested()

        // Act
        viewModel.onSignOutConfirmed()

        // Assert
        assertEquals(1, session.signOuts)
        assertFalse(viewModel.state.value.isSignOutConfirmVisible)
    }
}
