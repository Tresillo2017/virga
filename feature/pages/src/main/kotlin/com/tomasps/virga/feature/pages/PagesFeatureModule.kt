package com.tomasps.virga.feature.pages

import com.tomasps.virga.core.navigation.PagesProject
import com.tomasps.virga.core.navigation.PagesRoot
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.pages.data.PagesDeploymentsRepository
import com.tomasps.virga.feature.pages.data.PagesDomainsRepository
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import com.tomasps.virga.feature.pages.demo.PagesDemoRoutes
import com.tomasps.virga.feature.pages.deployments.DeploymentDetailRoute
import com.tomasps.virga.feature.pages.deployments.DeploymentDetailViewModel
import com.tomasps.virga.feature.pages.deployments.DeploymentsViewModel
import com.tomasps.virga.feature.pages.domains.DomainsViewModel
import com.tomasps.virga.feature.pages.list.PagesListRoute
import com.tomasps.virga.feature.pages.list.PagesListViewModel
import com.tomasps.virga.feature.pages.project.ProjectDetailRoute
import com.tomasps.virga.feature.pages.project.ProjectDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Koin bindings for the Pages feature. Repositories are single; each navigation entry gets its own ViewModel. */
val pagesKoinModule = module {
    single { PagesProjectsRepository(get()) }
    single { PagesDeploymentsRepository(get()) }
    single { PagesDomainsRepository(get()) }
    single { PagesDemoRoutes() } bind DemoRouteContributor::class
    viewModel { PagesListViewModel(get(), get()) }
    viewModel { params -> ProjectDetailViewModel(params.get(), get(), get()) }
    viewModel { params -> DomainsViewModel(params.get(), get(), get()) }
    viewModel { params -> DeploymentsViewModel(params.get(), get(), get()) }
    viewModel { params ->
        val projectName: String = params.get()
        val deploymentId: String = params.get()
        DeploymentDetailViewModel(projectName, deploymentId, get(), get(), get())
    }
}

/** Adds this feature's route keys and screens to the app's registry. :app calls it once at startup. */
fun registerPagesEntries(routes: RouteRegistry) {
    routes.route(PagesRoot.serializer()) { PagesListRoute() }
    routes.route(PagesProject.serializer()) { key -> ProjectDetailRoute(key.projectName) }
    routes.route(PagesDeploymentDetail.serializer()) { key -> DeploymentDetailRoute(key.projectName, key.deploymentId) }
}
