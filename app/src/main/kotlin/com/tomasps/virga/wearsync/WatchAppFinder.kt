package com.tomasps.virga.wearsync

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.tomasps.virga.core.wearsync.WatchCapability
import kotlinx.coroutines.tasks.await

/** Whether any paired watch has the Virga watch app. Throws when the lookup itself fails. */
fun interface WatchAppFinder {
    suspend fun hasWatchApp(): Boolean
}

/**
 * Asks the Data Layer for nodes advertising [WatchCapability.NAME]. FILTER_ALL, not FILTER_REACHABLE: a watch that is
 * only out of range still has the app, and treating it as gone would delete the item and wipe the watch.
 */
class GmsWatchAppFinder(context: Context) : WatchAppFinder {
    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)

    override suspend fun hasWatchApp(): Boolean =
        capabilityClient.getCapability(WatchCapability.NAME, CapabilityClient.FILTER_ALL).await().nodes.isNotEmpty()
}
