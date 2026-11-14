package com.tomasps.virga.wearsync

import android.content.Context
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.tomasps.virga.core.wearsync.HandoffCodec
import com.tomasps.virga.core.wearsync.HandoffPaths
import com.tomasps.virga.core.wearsync.HandoffPayload
import com.tomasps.virga.core.wearsync.HandoffTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Writes the handoff to the Wear Data Layer. Failures (no Play Services, no paired node) are swallowed: a user
 * without a watch must see no change. Nothing is logged, because the payload carries the token.
 */
class GmsHandoffTransport(context: Context) : HandoffTransport {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(payload: HandoffPayload) {
        runIgnoringFailure {
            val request = PutDataRequest.create(HandoffPaths.CREDENTIAL)
                .setData(HandoffCodec.encode(payload))
                .setUrgent()
            dataClient.putDataItem(request).await()
        }
    }

    override suspend fun clear() {
        runIgnoringFailure {
            val uri = android.net.Uri.Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .path(HandoffPaths.CREDENTIAL)
                .build()
            dataClient.deleteDataItems(uri).await()
        }
    }

    private suspend fun runIgnoringFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // ApiException, missing Play Services or a Data Layer error: nothing to do without a watch.
        }
    }
}
