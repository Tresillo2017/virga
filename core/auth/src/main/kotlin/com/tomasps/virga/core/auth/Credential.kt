package com.tomasps.virga.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the vault stores. Only the vault and the session repository see this type. */
@Serializable
sealed interface Credential {

    /** A pasted Cloudflare API token. toString is redacted so a debugger or log cannot print it. */
    @Serializable
    @SerialName("api_token")
    data class ApiToken(val token: String) : Credential {
        override fun toString(): String = "ApiToken(token=***)"
    }

    /** The built-in demo account. Stored so a relaunch lands back in demo mode. */
    @Serializable
    @SerialName("demo")
    data object Demo : Credential

    /** Reserved for OAuth (PKCE). No code path creates it yet; it fixes the vault format now. */
    @Serializable
    @SerialName("oauth_reserved")
    data object OAuthReserved : Credential
}
