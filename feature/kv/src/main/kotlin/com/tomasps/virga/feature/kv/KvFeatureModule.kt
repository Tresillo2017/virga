package com.tomasps.virga.feature.kv

import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.KvRoot
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.kv.addkey.KvAddKeyViewModel
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.GraphQlKvMetricsSource
import com.tomasps.virga.feature.kv.data.KvMetricsSource
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.data.SwitchingKvMetricsSource
import com.tomasps.virga.feature.kv.demo.DemoKvMetricsSource
import com.tomasps.virga.feature.kv.demo.KvDemoRoutes
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.files.ContentResolverKvFileStore
import com.tomasps.virga.feature.kv.files.KvFileStore
import com.tomasps.virga.feature.kv.importer.KvImportViewModel
import com.tomasps.virga.feature.kv.key.KvKeyDetailViewModel
import com.tomasps.virga.feature.kv.key.KvKeyWriter
import com.tomasps.virga.feature.kv.metrics.KvMetricsViewModel
import com.tomasps.virga.feature.kv.namespace.KvNamespaceViewModel
import com.tomasps.virga.feature.kv.namespaces.KvNamespacesViewModel
import com.tomasps.virga.feature.kv.ui.KvAddKeyRoute
import com.tomasps.virga.feature.kv.ui.KvImportRoute
import com.tomasps.virga.feature.kv.ui.KvKeyDetailRoute
import com.tomasps.virga.feature.kv.ui.KvNamespaceRoute
import com.tomasps.virga.feature.kv.ui.KvNamespacesRoute
import com.tomasps.virga.feature.kv.value.KvWriteThrottle
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

private const val MILLIS_PER_SECOND = 1_000L

/** Koin bindings for the KV feature: repositories, the write rules, file access, metrics, and the demo routes. */
val kvKoinModule = module {
    single { KvRepository(get()) }
    single { KvNamespaceChanges() }
    single<KvFileStore> { ContentResolverKvFileStore(get()) }
    single { KvWriteThrottle() }
    single { KvKeyWriter(get(), get(), { System.currentTimeMillis() / MILLIS_PER_SECOND }) }
    single { KvDemoStore() }
    single { KvDemoRoutes(get()) } bind DemoRouteContributor::class
    single { DemoKvMetricsSource() }
    single<KvMetricsSource> {
        SwitchingKvMetricsSource(
            demoMode = get(),
            live = GraphQlKvMetricsSource(get()),
            demo = get<DemoKvMetricsSource>(),
        )
    }
    viewModel { KvNamespacesViewModel(get(), get(), get()) }
    viewModel { params -> KvNamespaceViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> KvMetricsViewModel(params.get(), get(), get()) }
    viewModel { params -> KvKeyDetailViewModel(params.get(), params.get(), get(), get(), get(), get(), get()) }
    viewModel { params -> KvAddKeyViewModel(params.get(), get(), get(), get(), get(), get()) }
    viewModel { params -> KvImportViewModel(params.get(), get(), get(), get(), get(), get()) }
}

/** Adds this feature's route keys and screens to the app's registry. :app calls it once at startup. */
fun registerKvEntries(routes: RouteRegistry) {
    routes.route(KvRoot.serializer()) { KvNamespacesRoute() }
    routes.route(KvNamespace.serializer()) { KvNamespaceRoute(it) }
    routes.route(KvKeyDetail.serializer()) { KvKeyDetailRoute(it) }
    routes.route(KvAddKey.serializer()) { KvAddKeyRoute(it) }
    routes.route(KvImport.serializer()) { KvImportRoute(it) }
}
