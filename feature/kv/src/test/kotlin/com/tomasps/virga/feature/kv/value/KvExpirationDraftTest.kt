package com.tomasps.virga.feature.kv.value

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KvExpirationDraftTest {

    @Test
    fun `no expiration mode parses to None`() {
        val parsed = parseExpirationDraft(KvExpirationDraft(KvExpirationMode.NONE, ""), ZoneOffset.UTC) as ExpirationParse.Ok
        assertEquals(KvExpiration.None, parsed.expiration)
    }

    @Test
    fun `ttl input parses as whole seconds`() {
        val parsed = parseExpirationDraft(KvExpirationDraft(KvExpirationMode.TTL, "600"), ZoneOffset.UTC) as ExpirationParse.Ok
        assertEquals(KvExpiration.Ttl(600), parsed.expiration)
    }

    @Test
    fun `ttl input that is not a number is refused`() {
        val parsed = parseExpirationDraft(KvExpirationDraft(KvExpirationMode.TTL, "ten minutes"), ZoneOffset.UTC)
        assertEquals(ExpirationParse.Invalid(ExpirationInputProblem.NOT_A_NUMBER), parsed)
    }

    @Test
    fun `absolute input is read in the given zone`() {
        val parsed = parseExpirationDraft(
            KvExpirationDraft(KvExpirationMode.ABSOLUTE, "2030-01-01 00:00"),
            ZoneOffset.UTC,
        ) as ExpirationParse.Ok
        assertEquals(KvExpiration.Absolute(1_893_456_000L), parsed.expiration)
    }

    @Test
    fun `absolute input in the wrong shape is refused`() {
        val parsed = parseExpirationDraft(KvExpirationDraft(KvExpirationMode.ABSOLUTE, "tomorrow"), ZoneOffset.UTC)
        assertTrue(parsed is ExpirationParse.Invalid)
    }
}
