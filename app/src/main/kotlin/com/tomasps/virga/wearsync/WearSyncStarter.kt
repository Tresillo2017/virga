package com.tomasps.virga.wearsync

import android.content.Context
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts the publisher and keeps the heartbeat scheduled exactly while a session is signed in. */
fun startWearSync(
    scope: CoroutineScope,
    context: Context,
    session: SessionRepository,
    publisher: HandoffPublisher,
) {
    publisher.start(scope)
    scope.launch {
        session.state
            .filter { it !is SessionState.Restoring }
            .map { it is SessionState.SignedIn }
            .distinctUntilChanged()
            .collect { signedIn ->
                if (signedIn) HandoffHeartbeatWorker.schedule(context) else HandoffHeartbeatWorker.cancel(context)
            }
    }
}
