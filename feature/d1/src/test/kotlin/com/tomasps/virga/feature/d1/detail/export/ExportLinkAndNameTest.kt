package com.tomasps.virga.feature.d1.detail.export

import com.tomasps.virga.feature.d1.data.ExportFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private const val SIGNED = "https://example.invalid/dump?X-Amz-Signature=secret-token"

class ExportLinkAndNameTest {

    @Test
    fun `the signed link never appears in the text form of an export file or a poll that holds it`() {
        val file = ExportFile(filename = "backup.sql", signedUrl = SIGNED)

        assertFalse(file.toString().contains("secret-token"))
        assertFalse(file.toString().contains("example.invalid"))
    }

    @Test
    fun `a plain file name is kept as it is`() {
        assertEquals("backup-2026.sql", safeDownloadName("backup-2026.sql"))
    }

    @Test
    fun `path parts are dropped so a name cannot point outside the downloads folder`() {
        assertEquals("passwd.sql", safeDownloadName("../../etc/passwd.sql"))
        assertEquals("evil.sql", safeDownloadName("..\\..\\evil.sql"))
    }

    @Test
    fun `unsafe characters become underscores and leading dots are removed`() {
        assertEquals("my_dump_.sql", safeDownloadName("my dump?.sql"))
        assertEquals("sql", safeDownloadName("..sql"))
    }

    @Test
    fun `a missing or empty name falls back to the default, and a long name is capped`() {
        assertEquals(DEFAULT_EXPORT_FILENAME, safeDownloadName(null))
        assertEquals(DEFAULT_EXPORT_FILENAME, safeDownloadName("   "))
        assertEquals(120, safeDownloadName("a".repeat(500)).length)
    }
}
