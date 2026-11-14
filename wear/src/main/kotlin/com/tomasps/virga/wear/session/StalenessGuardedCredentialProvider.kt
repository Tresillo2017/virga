package com.tomasps.virga.wear.session

import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.network.CredentialProvider

/**
 * Enforces the 24-hour rule at the point of use, so a process that stays alive past the window stops sending the
 * token. On detecting staleness it moves the session to Stale, which also clears the active token.
 */
class StalenessGuardedCredentialProvider(
    private val active: ActiveCredential,
    private val session: WatchSessionRepository,
) : CredentialProvider {

    override suspend fun bearerToken(): String? {
        val token = active.bearerToken() ?: return null
        if (!session.isCheckInStale()) return token
        session.refreshStaleness()
        return null
    }
}
