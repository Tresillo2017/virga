package com.tomasps.virga.feature.d1

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.navigation.D1Root
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.data.ExportRepository
import com.tomasps.virga.feature.d1.data.MetricsRepository
import com.tomasps.virga.feature.d1.data.SqlRepository
import com.tomasps.virga.feature.d1.data.TableCatalogRepository
import com.tomasps.virga.feature.d1.data.TableRowsRepository
import com.tomasps.virga.feature.d1.data.TimeTravelRepository
import com.tomasps.virga.feature.d1.demo.D1DemoRoutes
import com.tomasps.virga.feature.d1.detail.D1DetailRoute
import com.tomasps.virga.feature.d1.detail.D1DetailViewModel
import com.tomasps.virga.feature.d1.detail.console.ConsoleViewModel
import com.tomasps.virga.feature.d1.detail.export.ExportViewModel
import com.tomasps.virga.feature.d1.detail.metrics.MetricsViewModel
import com.tomasps.virga.feature.d1.detail.settings.DatabaseSettingsViewModel
import com.tomasps.virga.feature.d1.detail.tables.TablesViewModel
import com.tomasps.virga.feature.d1.detail.timetravel.TimeTravelViewModel
import com.tomasps.virga.feature.d1.list.D1ListRoute
import com.tomasps.virga.feature.d1.list.D1ListViewModel
import com.tomasps.virga.feature.d1.list.DataStoreRecentlyDeletedLog
import com.tomasps.virga.feature.d1.list.RecentlyDeletedLog
import com.tomasps.virga.feature.d1.sql.QueryHistoryStore
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

private val Context.d1HistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "virga_d1_console_history",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Koin bindings for the D1 feature. Needs CloudflareApi, SessionRepository and a Context. */
val d1KoinModule = module {
    single { DatabasesRepository(get()) }
    single { SqlRepository(get()) }
    single { TableCatalogRepository(get()) }
    single { TableRowsRepository(get()) }
    single { TimeTravelRepository(get()) }
    single { ExportRepository(get()) }
    single { MetricsRepository(get()) }
    single { QueryHistoryStore(get<Context>().d1HistoryDataStore) }
    single { D1DemoRoutes() } bind DemoRouteContributor::class

    single { DataStoreRecentlyDeletedLog(get<Context>().d1HistoryDataStore) } bind RecentlyDeletedLog::class
    viewModel { D1ListViewModel(get(), get(), get()) }
    viewModel { params -> D1DetailViewModel(get(), get(), params.get<String>()) }
    viewModel { params -> TablesViewModel(get(), params.get<String>(), get(), get()) }
    viewModel { params -> ConsoleViewModel(get(), get(), get(), params.get<String>()) }
    viewModel { params -> TimeTravelViewModel(get(), get(), get(), params.get<String>()) }
    viewModel { params -> ExportViewModel(get(), get(), params.get<String>()) }
    viewModel { params -> DatabaseSettingsViewModel(get(), get(), get(), params.get<String>()) }
    viewModel { params -> MetricsViewModel(get(), get(), params.get<String>()) }
}

/** Adds this feature's route keys and screens to the app's registry. :app calls it once at startup. */
fun registerD1Entries(routes: RouteRegistry) {
    routes.route(D1Root.serializer()) { D1ListRoute() }
    routes.route(D1Database.serializer()) { key -> D1DetailRoute(key) }
}
