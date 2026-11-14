package com.tomasps.virga.wear.session

/** The wearer dismisses a stale connection: the watch forgets the token and the phone metadata. */
fun interface WatchDisconnect {
    suspend fun disconnect()
}
