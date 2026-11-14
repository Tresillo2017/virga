package com.tomasps.virga.core.wearsync

import kotlinx.serialization.json.Json

sealed interface HandoffDecode {
    data class Ok(val payload: HandoffPayload) : HandoffDecode
    data object Invalid : HandoffDecode
}

object HandoffCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(payload: HandoffPayload): ByteArray =
        json.encodeToString(HandoffPayload.serializer(), payload).encodeToByteArray()

    fun decode(bytes: ByteArray): HandoffDecode {
        val payload = try {
            json.decodeFromString(HandoffPayload.serializer(), bytes.decodeToString())
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException; covers corrupt/mismatched input.
            return HandoffDecode.Invalid
        }
        return if (payload.version > HandoffPayload.CURRENT_VERSION) HandoffDecode.Invalid else HandoffDecode.Ok(payload)
    }
}
