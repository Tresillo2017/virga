package com.tomasps.virga.core.network

import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Reads any outgoing body as bytes, whatever its kind (bytes, channel, or streamed writer). Used by the demo
 * engine and tests to inspect what the client sent. Binary bodies (KV values, R2 objects) stay exact.
 */
internal suspend fun readOutgoingBytes(content: OutgoingContent): ByteArray = when (content) {
    is OutgoingContent.NoContent -> ByteArray(0)
    is OutgoingContent.ByteArrayContent -> content.bytes()
    is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readBytes()
    is OutgoingContent.WriteChannelContent -> readStreamedBytes(content)
    else -> ByteArray(0)
}

private suspend fun readStreamedBytes(content: OutgoingContent.WriteChannelContent): ByteArray {
    val channel = ByteChannel(autoFlush = true)
    return coroutineScope {
        launch {
            try {
                content.writeTo(channel)
            } finally {
                channel.close()
            }
        }
        channel.readRemaining().readBytes()
    }
}

/** Text view of [readOutgoingBytes] for tests that inspect JSON bodies. Binary content should use the bytes. */
internal suspend fun readOutgoingBody(content: OutgoingContent): String = readOutgoingBytes(content).decodeToString()
