package com.tomasps.virga.wearsync

import kotlinx.coroutines.CoroutineScope

/**
 * Starts the publisher. It also owns the heartbeat: scheduled alongside a publish to a watch with the Virga app,
 * cancelled on sign-out or when no such watch exists.
 */
fun startWearSync(scope: CoroutineScope, publisher: HandoffPublisher) {
    publisher.start(scope)
}
