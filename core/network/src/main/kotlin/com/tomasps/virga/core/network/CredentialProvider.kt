package com.tomasps.virga.core.network

/**
 * Supplies the bearer token for the next request. core:auth implements this; core:network never
 * stores or logs the value. Return null when signed out, which fails the call before any network I/O.
 */
fun interface CredentialProvider {
    suspend fun bearerToken(): String?
}
