package com.tomasps.virga.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.auth.vault.AesGcmCipher
import com.tomasps.virga.core.auth.vault.AndroidKeystoreKey
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.auth.vault.SecretCipher
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.coreCommonKoinModule
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.CredentialProvider
import com.tomasps.virga.core.network.coreNetworkKoinModule
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.builds.demo.BuildsDemoRoutes
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.watch.BuildFetcher
import com.tomasps.virga.feature.builds.watch.DataStoreWatchStore
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import com.tomasps.virga.feature.builds.watch.WatchStore
import com.tomasps.virga.wear.alerts.RegistryWatchStarter
import com.tomasps.virga.wear.alerts.WatchStarter
import com.tomasps.virga.wear.alerts.WearBuildFetcher
import com.tomasps.virga.wear.alerts.WearBuildNotifier
import com.tomasps.virga.wear.alerts.WearWatchService
import com.tomasps.virga.wear.builds.WearBuilds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.tomasps.virga.wear.demo.WearDemoRoutes
import com.tomasps.virga.wear.builds.WearBuildsSource
import com.tomasps.virga.wear.surfaces.BuildSurfaceRefresher
import com.tomasps.virga.wear.surfaces.LatestBuildCache
import com.tomasps.virga.wear.summary.SummaryCache
import com.tomasps.virga.wear.summary.SummaryRefresher
import com.tomasps.virga.wear.summary.SummarySource
import com.tomasps.virga.wear.ui.BuildDetailViewModel
import com.tomasps.virga.wear.ui.BuildListViewModel
import com.tomasps.virga.wear.session.SentCredentialGuard
import com.tomasps.virga.wear.session.SessionSentCredentialGuard
import com.tomasps.virga.wear.session.StalenessGuardedCredentialProvider
import com.tomasps.virga.wear.session.WatchSessionPrefs
import com.tomasps.virga.wear.session.WatchSessionRepository
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.time.Instant

/** Corrupt files are replaced with an empty store instead of throwing on every read. */
private val Context.wearSecretDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "virga_wear_session",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val Context.wearPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "virga_wear_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val Context.wearSurfaceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "virga_wear_surface",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** The watch's own list of watched builds, kept apart from the phone's virga_watched_builds. */
private val Context.wearWatchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "virga_wear_watches",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** The summary tile's last good numbers, kept apart from the build cache. */
private val Context.wearSummaryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "virga_wear_summary",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val WATCH_STORE = named("wearWatchStore")
private val SURFACE_STORE = named("wearSurfaceStore")
private val SUMMARY_STORE = named("wearSummaryStore")
private val VAULT_STORE = named("wearVaultStore")
private val PREFS_STORE = named("wearPrefsStore")

/** The session state the screens follow. Qualified so a flow test can bind a fixed one in its place. */
val WATCH_SESSION_STATE = named("watchSessionState")

/**
 * The watch's own bindings, used instead of coreAuthKoinModule: that module also binds DefaultSessionRepository (the
 * phone's sign-in flow) and a single unqualified DataStore named virga_session. The watch keeps its token in
 * virga_wear_session and its non-secret handoff metadata in a second store. Pair with coreCommonKoinModule and
 * coreNetworkKoinModule, which supply DispatcherProvider, DemoMode and the API.
 */
val wearKoinModule = module {
    single<DataStore<Preferences>>(VAULT_STORE) { get<Context>().wearSecretDataStore }
    single<DataStore<Preferences>>(PREFS_STORE) { get<Context>().wearPrefsDataStore }
    single<DataStore<Preferences>>(SURFACE_STORE) { get<Context>().wearSurfaceDataStore }
    single<DataStore<Preferences>>(SUMMARY_STORE) { get<Context>().wearSummaryDataStore }
    single<SecretCipher> { AesGcmCipher { AndroidKeystoreKey.getOrCreate() } }
    single { CredentialVault(get(VAULT_STORE), get(), get()) }
    single { WatchSessionPrefs(get(PREFS_STORE)) }
    single { ActiveCredential() }
    single { BuildsDemoRoutes() } bind DemoRouteContributor::class
    single { WearDemoRoutes() } bind DemoRouteContributor::class
    single { WatchSessionRepository(get(), get(), get(), get(), System::currentTimeMillis) }
    // Enforces the 24-hour rule per request, not only when something remembers to call refreshStaleness().
    single<CredentialProvider> { StalenessGuardedCredentialProvider(get()) }
    // Ties each 401 to the credential that request actually sent, so only a rejection of the token in use wipes.
    single<SentCredentialGuard> { SessionSentCredentialGuard(get()) }
    single { BuildsRepository(get(), get()) }
    single(WATCH_SESSION_STATE) { get<WatchSessionRepository>().state }
    single { WearBuildsSource(repository = get(), credentials = get()) } bind WearBuilds::class
}

/**
 * What the tiles and complication read: the last good build and the last good 24h summary (no token in either) and the
 * refreshes that feed them.
 */
val wearSurfaceKoinModule = module {
    single { LatestBuildCache(get(SURFACE_STORE)) }
    single { BuildSurfaceRefresher(get(), get(WATCH_SESSION_STATE), get(), clock = Instant::now) }
    single { SummaryCache(get(SUMMARY_STORE)) }
    single { SummarySource(graphQl = get<CloudflareApi>().graphQl, credentials = get()) }
    single { SummaryRefresher(get(), get(WATCH_SESSION_STATE), get(), clock = Instant::now) }
}

/**
 * Builds the watch itself polls and alerts on: the watch registry in its own DataStore, the engine's fetcher over
 * [WearBuilds] (so a 401 wipes the session once), the notifier and the service launcher. The rate limiter is the one
 * in coreNetworkKoinModule.
 */
val wearAlertsKoinModule = module {
    single<DataStore<Preferences>>(WATCH_STORE) { get<Context>().wearWatchDataStore }
    single<WatchStore> { DataStoreWatchStore(get<DataStore<Preferences>>(WATCH_STORE)) }
    single { WatchRegistry(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default), System::currentTimeMillis) }
    single<BuildFetcher> { WearBuildFetcher(get()) }
    single { WearBuildNotifier(get<Context>()) }
    single<WatchStarter> { RegistryWatchStarter(get()) { WearWatchService.start(get<Context>()) } }
}

/** The screens' ViewModels. They read the session through [WATCH_SESSION_STATE] and the data through [WearBuilds]. */
val wearUiKoinModule = module {
    viewModel { BuildListViewModel(get(WATCH_SESSION_STATE), get()) }
    viewModel { (build: BuildSummary) -> BuildDetailViewModel(get(WATCH_SESSION_STATE), get(), get(), build) }
}

/** Every Koin module the watch app starts with. */
val wearKoinModules = listOf(
    coreCommonKoinModule,
    coreNetworkKoinModule,
    wearKoinModule,
    wearUiKoinModule,
    wearSurfaceKoinModule,
    wearAlertsKoinModule,
)
