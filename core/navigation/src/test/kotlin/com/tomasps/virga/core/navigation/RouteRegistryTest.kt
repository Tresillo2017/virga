package com.tomasps.virga.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

@Serializable
private data class FeatureDetail(val id: String) : NavKey

@Serializable
private data object FeatureHome : NavKey

@Serializable
private data class UnregisteredDetail(val id: String) : NavKey

private val stackSerializer = ListSerializer(PolymorphicSerializer(NavKey::class))

class RouteRegistryTest {

    @Test
    fun `registered keys survive a saved-state round trip inside a back stack`() {
        // Arrange
        val registry = RouteRegistry().apply {
            registerSharedKeys()
            route(FeatureDetail.serializer()) {}
            route(FeatureHome.serializer()) {}
        }
        val json = jsonFor(registry)
        val stack: List<NavKey> = listOf(HomeRoot, WorkerDetail("api"), FeatureDetail("x"), FeatureHome)

        // Act
        val encoded = json.encodeToString(stackSerializer, stack)
        val decoded = json.decodeFromString(stackSerializer, encoded)

        // Assert
        assertEquals(stack, decoded)
    }

    @Test
    fun `a key that was never registered cannot be saved`() {
        // Arrange
        val registry = RouteRegistry().apply { registerSharedKeys() }
        val json = jsonFor(registry)

        // Act and Assert
        assertThrows(SerializationException::class.java) {
            json.encodeToString(stackSerializer, listOf<NavKey>(UnregisteredDetail("y")))
        }
    }

    @Test
    fun `registering the same key twice fails loudly`() {
        // Arrange
        val registry = RouteRegistry().apply { registerSharedKeys() }

        // Act and Assert
        assertThrows(IllegalArgumentException::class.java) {
            registry.saveable(HomeRoot.serializer())
        }
    }

    @Test
    fun `shared keys register exactly the cross-feature route names`() {
        // Arrange
        val registry = RouteRegistry().apply { registerSharedKeys() }

        // Act
        val names = registry.registeredKeyNames()

        // Assert
        assertEquals(
            setOf(
                "HomeRoot", "ComputeRoot", "DomainsRoot", "StorageRoot", "AccountRoot",
                "WorkersRoot", "PagesRoot", "R2Root", "D1Root", "KvRoot",
                "SignIn", "Settings", "WorkerDetail", "BuildDetail", "PagesProject",
                "ZoneDetail", "DnsRecords", "R2Bucket", "D1Database", "KvNamespace",
            ),
            names.map { it.substringAfterLast('.') }.toSet(),
        )
    }

    @Test
    fun `two keys with the same simple name in different packages can both be registered`() {
        // Arrange: the simple name "Settings" is shared by the cross-feature key and a feature's own key.
        val registry = RouteRegistry().apply { registerSharedKeys() }

        // Act
        registry.saveable(otherpkg.Settings.serializer())

        // Assert
        assertEquals(true, registry.registeredKeyNames().contains(otherpkg.Settings::class.java.name))
    }

    @Test
    fun `a shared key can be routed by its owner after shared keys are declared`() {
        // Arrange
        val registry = RouteRegistry().apply { registerSharedKeys() }

        // Act
        registry.route(HomeRoot.serializer()) {}

        // Assert
        assertEquals(true, registry.registeredKeyNames().contains(HomeRoot::class.java.name))
    }

    @Test
    fun `routing the same key twice fails loudly`() {
        // Arrange
        val registry = RouteRegistry().apply { registerSharedKeys() }
        registry.route(HomeRoot.serializer()) {}

        // Act and Assert
        assertThrows(IllegalArgumentException::class.java) {
            registry.route(HomeRoot.serializer()) {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun jsonFor(registry: RouteRegistry): Json =
        Json { serializersModule = registry.savedStateConfiguration().serializersModule }
}
