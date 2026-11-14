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

    /** Phone and watch update independently: a newer phone may add fields an older watch does not know. */
    @Test
    fun unknownExtraKeysDecodeWithTheKnownFieldsIntact() {
        val json = """{"version":1,"token":"s3cret","isDemo":false,
            "accounts":[{"id":"a1","name":"Acme","plan":"pro"}],"selectedAccountId":"a1","sentAtMillis":42,
            "futureFlag":true,"futureObject":{"nested":[1,2]}}"""

        val decoded = HandoffCodec.decode(json.encodeToByteArray())

        val expected = HandoffPayload(1, "s3cret", false, listOf(HandoffAccount("a1", "Acme")), "a1", 42L)
        assertEquals(HandoffDecode.Ok(expected), decoded)
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
