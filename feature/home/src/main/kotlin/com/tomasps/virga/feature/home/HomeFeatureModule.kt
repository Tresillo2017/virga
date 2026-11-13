package com.tomasps.virga.feature.home

import com.tomasps.virga.core.navigation.HomeRoot
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.home.data.CloudflareHomeRepository
import com.tomasps.virga.feature.home.data.HomeDataRepository
import com.tomasps.virga.feature.home.demo.HomeDemoRoutes
import com.tomasps.virga.feature.home.home.HomeRoute
import com.tomasps.virga.feature.home.home.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Koin bindings for the Home feature. Needs the core:auth session, the core:network API and the builds gateway. */
val homeKoinModule = module {
    single { CloudflareHomeRepository(get()) } bind HomeDataRepository::class
    single { HomeDemoRoutes() } bind DemoRouteContributor::class
    viewModel { HomeViewModel(get(), get(), get()) }
}

/** Adds this feature's route keys and screens to the app's registry. :app calls it once at startup. */
fun registerHomeEntries(routes: RouteRegistry) {
    routes.route(HomeRoot.serializer()) { HomeRoute() }
}
