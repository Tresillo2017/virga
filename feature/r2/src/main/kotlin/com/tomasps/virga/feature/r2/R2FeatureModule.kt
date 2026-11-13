package com.tomasps.virga.feature.r2

import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.navigation.R2Root
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.r2.api.R2MetricsRepository
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.bucket.R2BucketViewModel
import com.tomasps.virga.feature.r2.buckets.R2RootViewModel
import com.tomasps.virga.feature.r2.bucket.R2BucketRoute
import com.tomasps.virga.feature.r2.buckets.R2RootRoute
import com.tomasps.virga.feature.r2.demo.R2DemoRoutes
import com.tomasps.virga.feature.r2.metrics.R2MetricsViewModel
import com.tomasps.virga.feature.r2.objects.R2ObjectsViewModel
import com.tomasps.virga.feature.r2.settings.R2CorsViewModel
import com.tomasps.virga.feature.r2.settings.R2LifecycleViewModel
import com.tomasps.virga.feature.r2.settings.R2SettingsViewModel
import com.tomasps.virga.feature.r2.transfer.AndroidR2DocumentStreams
import com.tomasps.virga.feature.r2.transfer.R2DocumentStreams
import com.tomasps.virga.feature.r2.transfer.R2TransferCoordinator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin bindings for the R2 feature. The transfer coordinator is a singleton on its own scope, so downloads and
 * uploads keep running while the user is on another screen, for as long as the process is alive.
 */
val r2KoinModule = module {
    single { R2Repository(get()) }
    single { R2MetricsRepository(get()) }
    single<R2DocumentStreams> { AndroidR2DocumentStreams(get()) }
    single { R2TransferCoordinator(get(), get(), transferScope()) }
    single { R2DemoRoutes() } bind DemoRouteContributor::class
    viewModel { R2RootViewModel(get(), get()) }
    viewModel { params -> R2BucketViewModel(get(), get(), params.get()) }
    viewModel { params -> R2ObjectsViewModel(get(), get(), get(), get(), params.get()) }
    viewModel { params -> R2SettingsViewModel(get(), get(), params.get()) }
    viewModel { params -> R2CorsViewModel(get(), get(), params.get()) }
    viewModel { params -> R2LifecycleViewModel(get(), get(), params.get()) }
    viewModel { params -> R2MetricsViewModel(get(), get(), params.get()) }
}

/**
 * The app-scoped scope transfers run in. Each transfer records its own failure, so the handler is only a last
 * resort that keeps an unexpected error from ending the process; the transfer panel is not updated by it.
 */
private fun transferScope(): CoroutineScope {
    val lastResort = CoroutineExceptionHandler { _, _ -> }
    return CoroutineScope(SupervisorJob() + Dispatchers.IO + lastResort)
}

/** Adds this feature's route keys and screens to the app's registry. :app calls it once at startup. */
fun registerR2Entries(routes: RouteRegistry) {
    routes.route(R2Root.serializer()) { R2RootRoute() }
    routes.route(R2Bucket.serializer()) { key -> R2BucketRoute(key.bucketName) }
}
