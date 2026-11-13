package com.tomasps.virga.core.auth.session

import kotlinx.serialization.Serializable

/** A Cloudflare account the token can see. Names and ids are not secret, so they are cached in plain prefs. */
@Serializable
data class CloudflareAccount(val id: String, val name: String = "")

/** Session as the UI sees it. [Restoring] is the cold-start state the splash waits on. */
sealed interface SessionState {
    data object Restoring : SessionState
    data object SignedOut : SessionState

    data class SignedIn(
        val accounts: List<CloudflareAccount>,
        val selectedAccountId: String?,
        val isDemo: Boolean,
    ) : SessionState {
        val selectedAccount: CloudflareAccount?
            get() = accounts.firstOrNull { it.id == selectedAccountId }
    }
}
