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
import com.tomasps.virga.core.network.CredentialProvider
import com.tomasps.virga.core.network.coreNetworkKoinModule
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.builds.demo.BuildsDemoRoutes
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.wear.builds.WearBuilds
import com.tomasps.virga.wear.demo.WearDemoRoutes
import com.tomasps.virga.wear.builds.WearBuildsSource
import com.tomasps.virga.wear.surfaces.BuildSurfaceRefresher
import com.tomasps.virga.wear.surfaces.LatestBuildCache
import com.tomasps.virga.wear.ui.BuildDetailViewModel
import com.tomasps.virga.wear.ui.BuildListViewModel
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

private val SURFACE_STORE = named("wearSurfaceStore")
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
    single<SecretCipher> { AesGcmCipher { AndroidKeystoreKey.getOrCreate() } }
    single { CredentialVault(get(VAULT_STORE), get(), get()) }
    single { WatchSessionPrefs(get(PREFS_STORE)) }
    single { ActiveCredential() }
    single { BuildsDemoRoutes() } bind DemoRouteContributor::class
    single { WearDemoRoutes() } bind DemoRouteContributor::class
    single { WatchSessionRepository(get(), get(), get(), get(), System::currentTimeMillis) }
    // Enforces the 24-hour rule per request, not only when something remembers to call refreshStaleness().
    single<CredentialProvider> { StalenessGuardedCredentialProvider(get(), get()) }
    single { BuildsRepository(get(), get()) }
    single(WATCH_SESSION_STATE) { get<WatchSessionRepository>().state }
    single {
        val session = get<WatchSessionRepository>()
        val credentials = get<CredentialProvider>()
        WearBuildsSource(
            repository = get(),
            onUnauthorized = { session.onUnauthorized() },
            hasToken = { credentials.bearerToken() != null },
        )
    } bind WearBuilds::class
}

/** What the tile and complication read: the last good build (no token) and the refresh that feeds both. */
val wearSurfaceKoinModule = module {
    single { LatestBuildCache(get(SURFACE_STORE)) }
    single { BuildSurfaceRefresher(get(), get(WATCH_SESSION_STATE), get(), clock = Instant::now) }
}

/** The screens' ViewModels. They read the session through [WATCH_SESSION_STATE] and the data through [WearBuilds]. */
val wearUiKoinModule = module {
    viewModel { BuildListViewModel(get(WATCH_SESSION_STATE), get()) }
    viewModel { (build: BuildSummary) -> BuildDetailViewModel(get(WATCH_SESSION_STATE), get(), build) }
}

/** Every Koin module the watch app starts with. */
val wearKoinModules = listOf(
    coreCommonKoinModule,
    coreNetworkKoinModule,
    wearKoinModule,
    wearUiKoinModule,
    wearSurfaceKoinModule,
)
