package com.tomasps.virga.feature.builds

import android.content.Context
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.WorkerBuilds
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.demo.BuildsDemoRoutes
import com.tomasps.virga.feature.builds.gateway.DefaultBuildsGateway
import com.tomasps.virga.feature.builds.gateway.RepositoryBuildFetcher
import com.tomasps.virga.feature.builds.ui.BuildConfigRoute
import com.tomasps.virga.feature.builds.ui.BuildConfigViewModel
import com.tomasps.virga.feature.builds.ui.BuildDetailRoute
import com.tomasps.virga.feature.builds.ui.BuildDetailViewModel
import com.tomasps.virga.feature.builds.ui.WorkerBuildsRoute
import com.tomasps.virga.feature.builds.ui.WorkerBuildsViewModel
import com.tomasps.virga.feature.builds.watch.AndroidWatchServiceStarter
import com.tomasps.virga.feature.builds.watch.BuildFetcher
import com.tomasps.virga.feature.builds.watch.DataStoreWatchStore
import com.tomasps.virga.feature.builds.watch.SummaryDismissal
import com.tomasps.virga.feature.builds.watch.WatchNotifier
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import com.tomasps.virga.feature.builds.watch.WatchServiceStarter
import com.tomasps.virga.feature.builds.watch.WatchStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Qualifier for the long-lived scope the gateway and registry save and restore in. */
private const val BUILDS_SCOPE = "builds-background"

/** Koin bindings for the builds feature. The gateway is created at startup so saved watches restore early. */
val buildsKoinModule = module {
    single(named(BUILDS_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { BuildsRepository(get(), get<DemoMode>()) }
    single { DataStoreWatchStore(get<Context>()) } bind WatchStore::class
    single { WatchRegistry(get(), get(named(BUILDS_SCOPE))) { System.currentTimeMillis() } }
    single { RepositoryBuildFetcher(get()) } bind BuildFetcher::class
    single { SummaryDismissal() }
    single { WatchNotifier(get<Context>(), get()) }
    single { AndroidWatchServiceStarter(get<Context>()) } bind WatchServiceStarter::class
    single(createdAtStart = true) {
        DefaultBuildsGateway(
            repository = get(),
            registry = get(),
            starter = get(),
            dismissal = get(),
            session = get<SessionRepository>(),
            scope = get(named(BUILDS_SCOPE)),
        )
    } bind BuildsGateway::class
    single { BuildsDemoRoutes() } bind DemoRouteContributor::class
    viewModel { params ->
        val key = params.get<WorkerBuilds>()
        WorkerBuildsViewModel(get(), key.accountId, key.scriptName)
    }
    viewModel { params ->
        val key = params.get<BuildDetail>()
        BuildDetailViewModel(get(), get(), key.accountId, key.buildUuid, key.workerName)
    }
    viewModel { params ->
        val key = params.get<BuildConfigScreen>()
        BuildConfigViewModel(get(), key.accountId, key.scriptName)
    }
}

/** Adds this feature's route keys and screens to the app's registry. :app calls it once at startup. */
fun registerBuildsEntries(routes: RouteRegistry) {
    routes.route(BuildDetail.serializer()) { key -> BuildDetailRoute(key) }
    routes.route(WorkerBuilds.serializer()) { key -> WorkerBuildsRoute(key) }
    routes.route(BuildConfigScreen.serializer()) { key -> BuildConfigRoute(key) }
}
