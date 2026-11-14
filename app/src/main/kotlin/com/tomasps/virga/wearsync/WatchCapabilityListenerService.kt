package com.tomasps.virga.wearsync

import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.WearableListenerService
import com.tomasps.virga.core.wearsync.WatchCapability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/**
 * Woken by Play services when a watch gains or loses the Virga watch app, even while the phone app is not running
 * (a manifest listener, unlike CapabilityClient.addListener, which lives only as long as the process). The publisher
 * then publishes or clears to match. The work runs to completion inside the callback (a background thread) because
 * the system may destroy the service as soon as it returns; heartbeat-style waiting covers a cold-started session.
 */
class WatchCapabilityListenerService : WearableListenerService() {

    private val publisher: HandoffPublisher by inject()

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != WatchCapability.NAME) return
        runBlocking {
            try {
                publisher.reevaluate()
            } catch (error: CancellationException) {
                throw error
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.w("VirgaWear", "Capability re-evaluation failed: ${error::class.java.name}")
            }
        }
    }
}
