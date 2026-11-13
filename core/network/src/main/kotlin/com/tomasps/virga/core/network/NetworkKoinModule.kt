package com.tomasps.virga.core.network

import android.util.Log
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import org.koin.dsl.module

/**
 * Needs a [CredentialProvider] binding from core:auth. Each feature registers its routes with
 * `single { MyDemoRoutes() } bind DemoRouteContributor::class`, one concrete type per feature.
 */
val coreNetworkKoinModule = module {
    single { DemoMode() }
    single { RateLimiter() }
    single {
        DemoRouteRegistry(logger = ApiLogger { message -> Log.w(DEMO_LOG_TAG, message) }).also { registry ->
            getAll<DemoRouteContributor>().forEach { it.contribute(registry) }
        }
    }
    single {
        CloudflareApi.create(
            credentials = get(),
            demoMode = get(),
            demoRoutes = get(),
            rateLimiter = get(),
        )
    }
    single<GraphQlClient> { get<CloudflareApi>().graphQl }
}

private const val DEMO_LOG_TAG = "VirgaDemo"
