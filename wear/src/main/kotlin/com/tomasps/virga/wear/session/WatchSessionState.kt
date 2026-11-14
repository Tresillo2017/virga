package com.tomasps.virga.wear.session

sealed interface WatchSessionState {
    /** Before [WatchSessionRepository.restore] has settled. */
    data object Restoring : WatchSessionState

    /** No usable credential, or none that names an account the phone actually listed. */
    data object NotConnected : WatchSessionState

    /** A credential is stored but the phone has not checked in for over 24 hours, so it is not used. */
    data object Stale : WatchSessionState

    data class Connected(val accountId: String, val isDemo: Boolean) : WatchSessionState
}
