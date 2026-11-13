package com.tomasps.virga.feature.workers

import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.navigation.WorkersRoot
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.workers.data.WorkersRepository
import com.tomasps.virga.feature.workers.demo.WorkersDemoRoutes
import com.tomasps.virga.feature.workers.detail.WorkerDetailRoute
import com.tomasps.virga.feature.workers.detail.WorkerDetailViewModel
import com.tomasps.virga.feature.workers.list.WorkersListRoute
import com.tomasps.virga.feature.workers.metrics.WorkerMetricsRepository
import com.tomasps.virga.feature.workers.metrics.WorkerMetricsViewModel
import com.tomasps.virga.feature.workers.list.WorkersListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Koin bindings for the Workers feature. */
val workersKoinModule = module {
    single { WorkersRepository(get()) }
    single { WorkerMetricsRepository(get()) }
    single { WorkersDemoRoutes() } bind DemoRouteContributor::class
    viewModel { WorkersListViewModel(get(), get(), get<BuildsGateway>()) }
    viewModel { params -> WorkerMetricsViewModel(get(), get(), params.get<String>()) }
    viewModel { params ->
        WorkerDetailViewModel(
            session = get(),
            repository = get(),
            builds = get<BuildsGateway>(),
            scriptName = params.get<String>(),
        )
    }
}

/** Adds this feature's route keys and screens to the app's registry. :app calls it once at startup. */
fun registerWorkersEntries(routes: RouteRegistry) {
    routes.route(WorkersRoot.serializer()) { WorkersListRoute() }
    routes.route(WorkerDetail.serializer()) { WorkerDetailRoute(it) }
}
