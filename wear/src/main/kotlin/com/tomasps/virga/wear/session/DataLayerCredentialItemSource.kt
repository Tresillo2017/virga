package com.tomasps.virga.wear.session

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.tomasps.virga.core.wearsync.HandoffPaths
import kotlinx.coroutines.tasks.await

/** Reads the credential item the Data Layer already holds on this watch. Local storage only, no network. */
class DataLayerCredentialItemSource(context: Context) : CredentialItemSource {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun current(): ByteArray? {
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(HandoffPaths.CREDENTIAL).build()
        val items = dataClient.getDataItems(uri).await()
        return try {
            items.firstOrNull { it.uri.path == HandoffPaths.CREDENTIAL }?.data
        } finally {
            items.release()
        }
    }
}
