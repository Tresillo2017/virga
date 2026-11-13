package com.tomasps.virga.shell

import androidx.navigation3.runtime.NavKey
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.HomeRoot
import com.tomasps.virga.core.navigation.SignIn
import com.tomasps.virga.core.navigation.WorkerDetail
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shell builds the one registry for the whole app. Building it here catches a duplicate route key, a key
 * with no screen, or a key that cannot be saved, before the app crashes on first composition.
 */
class AppRoutesTest {

    private val stackSerializer = ListSerializer(PolymorphicSerializer(NavKey::class))

    @Test
    fun `the app registry builds without a duplicate key or a missing saved-state entry`() {
        val registry = buildRouteRegistry()

        assertTrue(registry.registeredKeyNames().contains(BuildDetail::class.java.name))
        assertTrue(registry.registeredKeyNames().size > 20)
    }

    @Test
    fun `every cross-feature key round trips through the saved back stack`() {
        val registry = buildRouteRegistry()
        val json = Json { serializersModule = registry.savedStateConfiguration().serializersModule }
        val stack: List<NavKey> = listOf(
            HomeRoot,
            SignIn,
            WorkerDetail("api"),
            BuildDetail(accountId = "acc", buildUuid = "b1", workerName = "api"),
        )

        val decoded = json.decodeFromString(stackSerializer, json.encodeToString(stackSerializer, stack))

        assertEquals(stack, decoded)
    }

    @Test
    fun `every shared key has a screen`() {
        val entryProvider = buildRouteRegistry().buildEntryProvider()

        // Resolving the key throws if no screen is registered for it; the screen itself is not composed here.
        entryProvider(BuildDetail(accountId = "acc", buildUuid = "b1", workerName = "api"))
        entryProvider(SignIn)
        entryProvider(HomeRoot)
    }
}
