package com.tomasps.virga.feature.account.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThemeRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): androidx.datastore.core.DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    ) { File(folder.root, "settings.preferences_pb") }

    @Test
    fun `a fresh install follows the system theme`() = runBlocking {
        // Arrange
        val repository = ThemeRepository(store())

        // Act
        val mode = repository.mode.first()

        // Assert
        assertEquals(ThemeMode.System, mode)
    }

    @Test
    fun `a chosen theme is remembered by the next repository on the same store`() = runBlocking {
        // Arrange
        val store = store()
        ThemeRepository(store).set(ThemeMode.Dark)

        // Act
        val mode = ThemeRepository(store).mode.first()

        // Assert
        assertEquals(ThemeMode.Dark, mode)
    }

    @Test
    fun `an unknown stored value falls back to the system theme`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("neon"))
        assertEquals(ThemeMode.Light, ThemeMode.fromStorage("light"))
    }
}
