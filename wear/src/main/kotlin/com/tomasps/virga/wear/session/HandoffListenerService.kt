package com.tomasps.virga.wear.session

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.tomasps.virga.core.wearsync.HandoffPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Receives the phone's credential item. Never logs the payload: it carries the token.
 */
class HandoffListenerService : WearableListenerService() {

    private val repository: WatchSessionRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDataChanged(events: DataEventBuffer) {
        // The buffer is released when this returns, so copy what is needed before launching.
        val changes = events
            .filter { it.dataItem.uri.path == HandoffPaths.CREDENTIAL }
            .map { event -> event.type to event.dataItem.data }
        for ((type, bytes) in changes) {
            when (type) {
                DataEvent.TYPE_CHANGED -> bytes?.let { scope.launch { repository.applyEncoded(it) } }
                DataEvent.TYPE_DELETED -> scope.launch { repository.clear() }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
