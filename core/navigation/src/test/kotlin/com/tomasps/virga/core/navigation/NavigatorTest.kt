package com.tomasps.virga.core.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {

    @Test
    fun `navigate pushes a key on top of the stack`() {
        // Arrange
        val stack = mutableListOf<NavKey>(HomeRoot)
        val navigator = Navigator(stack) {}

        // Act
        navigator.navigate(WorkerDetail("api"))

        // Assert
        assertEquals(listOf<NavKey>(HomeRoot, WorkerDetail("api")), stack)
    }

    @Test
    fun `back pops one key and reports true while something is below`() {
        // Arrange
        val stack = mutableListOf<NavKey>(HomeRoot, WorkerDetail("api"))
        val navigator = Navigator(stack) {}

        // Act
        val popped = navigator.back()

        // Assert
        assertTrue(popped)
        assertEquals(listOf<NavKey>(HomeRoot), stack)
    }

    @Test
    fun `back on the last key reports false and keeps it so the app can exit`() {
        // Arrange
        val stack = mutableListOf<NavKey>(HomeRoot)
        val navigator = Navigator(stack) {}

        // Act
        val popped = navigator.back()

        // Assert
        assertFalse(popped)
        assertEquals(listOf<NavKey>(HomeRoot), stack)
    }

    @Test
    fun `replaceAll collapses the stack to one key, as a bottom tab switch does`() {
        // Arrange
        val stack = mutableListOf<NavKey>(HomeRoot, WorkerDetail("api"), PagesProject("site"))
        val navigator = Navigator(stack) {}

        // Act
        navigator.replaceAll(DomainsRoot)

        // Assert
        assertEquals(listOf<NavKey>(DomainsRoot), stack)
    }

    @Test
    fun `openInBrowser hands the url to the injected opener unchanged`() {
        // Arrange
        val opened = mutableListOf<String>()
        val navigator = Navigator(mutableListOf<NavKey>(HomeRoot)) { opened += it }

        // Act
        navigator.openInBrowser("https://dash.cloudflare.com/")

        // Assert
        assertEquals(listOf("https://dash.cloudflare.com/"), opened)
    }

    @Test
    fun `openInBrowser opens an allowed link and reports true`() {
        val opened = mutableListOf<String>()
        val navigator = Navigator(mutableListOf(HomeRoot)) { opened += it }

        val result = navigator.openInBrowser("https://dash.cloudflare.com/profile/api-tokens")

        assertTrue(result)
        assertEquals(listOf("https://dash.cloudflare.com/profile/api-tokens"), opened)
    }

    @Test
    fun `openInBrowser refuses any other link and opens nothing`() {
        val opened = mutableListOf<String>()
        val navigator = Navigator(mutableListOf(HomeRoot)) { opened += it }

        val result = navigator.openInBrowser("https://attacker.example/phish")

        assertFalse(result)
        assertTrue(opened.isEmpty())
    }
}
