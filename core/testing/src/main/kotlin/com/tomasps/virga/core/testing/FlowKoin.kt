package com.tomasps.virga.core.testing

import android.content.Context
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.common.DispatcherProvider
import com.tomasps.virga.core.navigation.PendingNavigation
import com.tomasps.virga.core.network.CredentialProvider
import com.tomasps.virga.core.network.coreNetworkKoinModule
import com.tomasps.virga.core.network.demo.DemoMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The bindings a demo flow needs that the real app gets from core:auth and :app. The session is a demo session that
 * is already signed in. Dispatchers are unconfined, so repository work finishes inline and a test never waits on a
 * thread pool. The real core:network graph is included, with the demo engine on.
 */
internal fun flowKoinModules(
    context: Context,
    accounts: List<CloudflareAccount>,
    selectedAccountId: String,
    feature: List<Module>,
): List<Module> = listOf(
    coreNetworkKoinModule,
    module {
        single<Context> { context }
        single<DispatcherProvider> { UnconfinedDispatcherProvider }
        single { PendingNavigation() }
        single { DemoMode(initiallyEnabled = true) }
        single { ActiveCredential() }
        single<CredentialProvider> { get<ActiveCredential>() }
        single<SessionRepository> { DemoSessionRepository(accounts, selectedAccountId) }
    },
) + feature

private object UnconfinedDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}
