package com.tomasps.virga.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlin.reflect.KClass

/**
 * One registry per app. Each feature adds its keys in its own `registerXEntries(routes)` function, which
 * does two things per key: makes the key saveable in the back stack, and maps the key to its screen.
 * :app builds the saved-state configuration and the entry provider from the same registry, so a key cannot
 * be shown without being saveable, and a saveable key that has no screen fails at the first navigation.
 *
 * Not thread-safe. Fill it on the main thread before NavDisplay first composes.
 */
class RouteRegistry {
    private val names = linkedSetOf<String>()
    private val saveBlocks = mutableListOf<SerializersModuleBuilder.() -> Unit>()
    private val routedNames = mutableSetOf<String>()
    private val entryBlocks = mutableListOf<EntryProviderScope<NavKey>.() -> Unit>()

    /** Makes [keyClass] saveable without a screen. Declaring the same key twice is an error. */
    fun <K : NavKey> saveable(keyClass: KClass<K>, serializer: KSerializer<K>) {
        val name = keyNameOf(keyClass)
        require(names.add(name)) { "Route key $name is already registered" }
        saveBlocks += { polymorphic(NavKey::class) { subclass(keyClass, serializer) } }
    }

    inline fun <reified K : NavKey> saveable(serializer: KSerializer<K>) {
        saveable(K::class, serializer)
    }

    /**
     * Maps [keyClass] to [content]. The key is made saveable here unless [registerSharedKeys] already did
     * that, so a shared key may be routed by its owner without being declared twice. A key can have only
     * one screen.
     */
    fun <K : NavKey> route(keyClass: KClass<K>, serializer: KSerializer<K>, content: @Composable (K) -> Unit) {
        val name = keyNameOf(keyClass)
        require(routedNames.add(name)) { "Route key $name already has a screen" }
        if (name !in names) saveable(keyClass, serializer)
        // The content key feeds rememberSaveableStateHolder, which only accepts Bundle-storable values on Android.
        // toString() of a key is unique per instance (data classes list their fields), so it is a safe key.
        entryBlocks += { addEntryProvider(keyClass, { it.toString() }, emptyMap(), content) }
    }

    inline fun <reified K : NavKey> route(serializer: KSerializer<K>, noinline content: @Composable (K) -> Unit) {
        route(K::class, serializer, content)
    }

    /**
     * Fully qualified class names of every saveable key, for tests and diagnostics. Keys are identified by their
     * full name, so two features may each declare a key with the same simple name (for example `Settings`).
     */
    fun registeredKeyNames(): Set<String> = names.toSet()

    fun savedStateConfiguration(): SavedStateConfiguration = SavedStateConfiguration {
        serializersModule = kotlinx.serialization.modules.SerializersModule {
            saveBlocks.forEach { it() }
        }
    }

    fun buildEntryProvider(): (NavKey) -> NavEntry<NavKey> = entryProvider<NavKey> {
        entryBlocks.forEach { it() }
    }
}

/**
 * Declares the cross-feature keys in [HomeRoot]..[KvNamespace] as saveable, so the shared keys are known
 * before any feature registers a screen. Call it once, first. Screens still come from the owning features.
 */
fun RouteRegistry.registerSharedKeys() {
    saveable(HomeRoot.serializer())
    saveable(ComputeRoot.serializer())
    saveable(DomainsRoot.serializer())
    saveable(StorageRoot.serializer())
    saveable(AccountRoot.serializer())
    saveable(WorkersRoot.serializer())
    saveable(PagesRoot.serializer())
    saveable(R2Root.serializer())
    saveable(D1Root.serializer())
    saveable(KvRoot.serializer())
    saveable(SignIn.serializer())
    saveable(Settings.serializer())
    saveable(WorkerDetail.serializer())
    saveable(BuildDetail.serializer())
    saveable(PagesProject.serializer())
    saveable(ZoneDetail.serializer())
    saveable(DnsRecords.serializer())
    saveable(R2Bucket.serializer())
    saveable(D1Database.serializer())
    saveable(KvNamespace.serializer())
}

/** Binary class name: unique per class, unlike the simple name, which two features can share. */
private fun keyNameOf(keyClass: KClass<*>): String = keyClass.java.name
