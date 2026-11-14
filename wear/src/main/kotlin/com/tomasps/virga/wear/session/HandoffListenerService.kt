package com.tomasps.virga.wear.session

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.tomasps.virga.core.wearsync.HandoffPaths
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

private const val TAG = "VirgaWear"

/**
 * Receives the phone's credential item. The system may destroy this service as soon as [onDataChanged] returns, so the
 * events are processed to completion, in arrival order, inside the callback (it runs on a background thread). Never
 * logs the payload, which carries the token; errors are logged by class name only.
 */
class HandoffListenerService : WearableListenerService() {

    private val repository: WatchSessionRepository by inject()

    override fun onDataChanged(events: DataEventBuffer) {
        // The buffer is recycled after this returns, so copy what is needed first.
        val copied = events
            .filter { it.dataItem.uri.path == HandoffPaths.CREDENTIAL }
            .mapNotNull { event ->
                when (event.type) {
                    DataEvent.TYPE_CHANGED -> event.dataItem.data?.let { HandoffEvent.Changed(it) }
                    DataEvent.TYPE_DELETED -> HandoffEvent.Deleted
                    else -> null
                }
            }
        val handler = HandoffEventHandler(repository) { Log.e(TAG, "Handoff event failed: ${it::class.java.name}") }
        runBlocking { handler.handle(copied) }
    }
}
