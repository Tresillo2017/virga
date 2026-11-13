package com.tomasps.virga.feature.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.tomasps.virga.core.navigation.AccountRoot
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.Settings
import com.tomasps.virga.core.navigation.SignIn
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.feature.account.R
import com.tomasps.virga.feature.account.about.AboutRoute
import com.tomasps.virga.feature.account.account.AccountHomeViewModel
import com.tomasps.virga.feature.account.account.AccountRoute
import com.tomasps.virga.feature.account.demo.AccountDemoRoutes
import com.tomasps.virga.feature.account.details.AccountDetailsRepository
import com.tomasps.virga.feature.account.settings.SettingsRoute
import com.tomasps.virga.feature.account.settings.SettingsViewModel
import com.tomasps.virga.feature.account.settings.ThemeRepository
import com.tomasps.virga.feature.account.signin.SignInRoute
import com.tomasps.virga.feature.account.signin.SignInViewModel
import com.tomasps.virga.feature.account.switcher.AccountSwitcherRoute
import com.tomasps.virga.feature.account.switcher.AccountSwitcherViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "virga_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Koin bindings for the account feature. Needs a Context and the core:auth and core:network bindings. */
val accountKoinModule = module {
    single { ThemeRepository(get<Context>().themeDataStore) }
    single {
        val context: Context = get()
        AccountDemoRoutes(
            personalName = context.getString(R.string.account_demo_name_personal),
            stagingName = context.getString(R.string.account_demo_name_staging),
        )
    } bind DemoRouteContributor::class
    viewModel { SignInViewModel(get()) }
    single { AccountDetailsRepository(get()) }
    viewModel { AccountHomeViewModel(get(), get()) }
    viewModel { AccountSwitcherViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
}

/** Adds this feature's keys and screens to the app's registry. :app calls it once at startup. */
fun registerAccountEntries(routes: RouteRegistry) {
    routes.route(SignIn.serializer()) { SignInRoute() }
    routes.route(AccountRoot.serializer()) { AccountRoute() }
    routes.route(Settings.serializer()) { SettingsRoute() }
    routes.route(AccountSwitcher.serializer()) { AccountSwitcherRoute() }
    routes.route(AccountAbout.serializer()) { AboutRoute() }
}
