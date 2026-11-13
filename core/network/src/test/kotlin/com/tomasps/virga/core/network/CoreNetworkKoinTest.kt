package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.core.module.Module
import org.koin.core.context.stopKoin
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class CoreNetworkKoinTest {
    private class FirstFeatureRoutes : DemoRouteContributor {
        override fun contribute(registry: DemoRouteRegistry) {
            registry.get("/first") { DemoReply.ok(kotlinx.serialization.json.JsonNull) }
        }
    }

    private class SecondFeatureRoutes : DemoRouteContributor {
        override fun contribute(registry: DemoRouteRegistry) {
            registry.get("/second") { DemoReply.ok(kotlinx.serialization.json.JsonNull) }
        }
    }

    private val firstFeature: Module = module { single { FirstFeatureRoutes() } bind DemoRouteContributor::class }
    private val secondFeature: Module = module { single { SecondFeatureRoutes() } bind DemoRouteContributor::class }
    private val credentials: Module = module { single { CredentialProvider { "token" } } }

    @Test
    fun `two feature modules contribute demo routes through getAll`() {
        val koin = koinApplication {
            modules(coreNetworkKoinModule, firstFeature, secondFeature, credentials)
        }.koin
        val contributors = koin.getAll<DemoRouteContributor>()
        assertEquals(2, contributors.size)
        assertNotNull(koin.get<CloudflareApi>())
        assertNotNull(koin.get<RateLimiter>())
        assertNotNull(koin.get<DemoMode>())
        stopKoin()
    }
}
