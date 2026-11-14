package com.tomasps.virga.core.wearsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffCodecTest {
    private val payload = HandoffPayload(
        version = HandoffPayload.CURRENT_VERSION,
        token = "s3cret",
        isDemo = false,
        accounts = listOf(HandoffAccount("a1", "Acme"), HandoffAccount("a2", "Beta")),
        selectedAccountId = "a2",
        sentAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun roundTripsAllFields() {
        val decoded = HandoffCodec.decode(HandoffCodec.encode(payload))
        assertEquals(HandoffDecode.Ok(payload), decoded)
    }

    @Test
    fun toStringNeverContainsToken() {
        assertFalse(payload.toString().contains("s3cret"))
    }

    @Test
    fun corruptBytesDecodeToInvalid() {
        assertEquals(HandoffDecode.Invalid, HandoffCodec.decode(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun newerVersionDecodesToInvalid() {
        val newer = payload.copy(version = HandoffPayload.CURRENT_VERSION + 1)
        assertEquals(HandoffDecode.Invalid, HandoffCodec.decode(HandoffCodec.encode(newer)))
    }

    @Test
    fun demoPayloadWithNullTokenRoundTrips() {
        val demo = payload.copy(token = null, isDemo = true, accounts = emptyList(), selectedAccountId = null)
        val decoded = HandoffCodec.decode(HandoffCodec.encode(demo))
        assertTrue(decoded is HandoffDecode.Ok)
        assertEquals(demo, (decoded as HandoffDecode.Ok).payload)
    }
}
