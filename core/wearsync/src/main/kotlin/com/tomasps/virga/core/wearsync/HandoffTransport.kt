package com.tomasps.virga.core.wearsync

/** Phone-side publisher of the credential handoff. */
interface HandoffTransport {
    suspend fun publish(payload: HandoffPayload)
    suspend fun clear()
}
