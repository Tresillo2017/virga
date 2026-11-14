package com.tomasps.virga.wear.session

import com.tomasps.virga.core.network.CredentialProvider

/**
 * Enforces the 24-hour rule at the point of use, so a process that stays alive past the window stops sending the
 * token. On detecting staleness it moves the session to Stale, which also clears the active token. Every answer is
 * recorded for the request's [SentCredentialGuard]: the generation of the token handed out, or none.
 */
class StalenessGuardedCredentialProvider(
    private val session: WatchSessionRepository,
) : CredentialProvider {

    override suspend fun bearerToken(): String? {
        val issued = session.issuedCredential()
        if (issued != null && !session.isCheckInStale()) {
            recordSentCredential(issued.generation)
            return issued.token
        }
        recordSentCredential(null)
        if (issued != null) session.refreshStaleness()
        return null
    }
}
