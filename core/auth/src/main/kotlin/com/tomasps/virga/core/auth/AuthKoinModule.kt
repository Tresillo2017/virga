package com.tomasps.virga.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.auth.session.DefaultSessionRepository
import com.tomasps.virga.core.auth.session.SessionPreferences
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.vault.AesGcmCipher
import com.tomasps.virga.core.auth.vault.AndroidKeystoreKey
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.auth.vault.SecretCipher
import com.tomasps.virga.core.network.CredentialProvider
import org.koin.dsl.module

/** A corrupt file is replaced with an empty store instead of throwing on every read, which would crash each launch. */
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "virga_session",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Needs an Android Context (androidContext() in :app). Binds [CredentialProvider] to [ActiveCredential] so
 * core:network can read the token without knowing about the vault.
 */
val coreAuthKoinModule = module {
    single<DataStore<Preferences>> { get<Context>().sessionDataStore }
    single<SecretCipher> { AesGcmCipher { AndroidKeystoreKey.getOrCreate() } }
    single { CredentialVault(get(), get(), get()) }
    single { SessionPreferences(get()) }
    single { ActiveCredential() }
    single<CredentialProvider> { get<ActiveCredential>() }
    single<SessionRepository> { DefaultSessionRepository(get(), get(), get(), get(), get()) }
}
