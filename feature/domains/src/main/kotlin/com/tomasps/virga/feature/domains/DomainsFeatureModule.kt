package com.tomasps.virga.feature.domains

import com.tomasps.virga.core.navigation.DnsRecords
import com.tomasps.virga.core.navigation.DomainsRoot
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.ZoneDetail
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.domains.analytics.ZoneTrafficRepository
import com.tomasps.virga.feature.domains.demo.DomainsDemoRoutes
import com.tomasps.virga.feature.domains.dns.DnsRecordRepository
import com.tomasps.virga.feature.domains.dns.DnsRecordsScreenRoute
import com.tomasps.virga.feature.domains.dns.DnsRecordsViewModel
import com.tomasps.virga.feature.domains.dns.DnssecRepository
import com.tomasps.virga.feature.domains.dns.DnssecViewModel
import com.tomasps.virga.feature.domains.dns.ZoneDnsSettingsRepository
import com.tomasps.virga.feature.domains.dns.ZoneDnsSettingsViewModel
import com.tomasps.virga.feature.domains.zone.ZoneDetailRoute
import com.tomasps.virga.feature.domains.zone.ZoneDetailViewModel
import com.tomasps.virga.feature.domains.zones.ZoneConnectionsRepository
import com.tomasps.virga.feature.domains.zones.ZoneRepository
import com.tomasps.virga.feature.domains.zones.ZoneSettingsRepository
import com.tomasps.virga.feature.domains.zones.list.ZoneListRoute
import com.tomasps.virga.feature.domains.zones.list.ZoneListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Koin bindings for the Domains feature. Each ViewModel is a `viewModel { }` so every navigation entry gets its own. */
val domainsKoinModule = module {
    single { ZoneRepository(get()) }
    single { ZoneSettingsRepository(get()) }
    single { ZoneConnectionsRepository(get()) }
    single { ZoneTrafficRepository(get()) }
    single { DnsRecordRepository(get()) }
    single { DnssecRepository(get()) }
    single { ZoneDnsSettingsRepository(get()) }
    single { DomainsDemoRoutes() } bind DemoRouteContributor::class
    viewModel { ZoneListViewModel(get(), get()) }
    viewModel { params -> ZoneDetailViewModel(params.get<String>(), get(), get(), get(), get()) }
    viewModel { params -> DnsRecordsViewModel(params.get<String>(), get(), get()) }
    viewModel { params -> DnssecViewModel(params.get<String>(), get()) }
    viewModel { params -> ZoneDnsSettingsViewModel(params.get<String>(), get()) }
}

/** Adds this feature's route keys and screens to the app's registry. :app calls it once at startup. */
fun registerDomainsEntries(routes: RouteRegistry) {
    routes.route(DomainsRoot.serializer()) { ZoneListRoute() }
    routes.route(ZoneDetail.serializer()) { key -> ZoneDetailRoute(key.zoneId) }
    routes.route(DnsRecords.serializer()) { key -> DnsRecordsScreenRoute(key.zoneId) }
}
