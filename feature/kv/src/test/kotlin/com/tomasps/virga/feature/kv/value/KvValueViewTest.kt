package com.tomasps.virga.feature.kv.value

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KvValueViewTest {

    @Test
    fun `valid utf-8 text is shown as text`() {
        // Arrange
        val bytes = "hello, world".encodeToByteArray()

        // Act
        val view = classifyValue(bytes)

        // Assert
        assertEquals(KvValueView.Text(text = "hello, world", isJson = false), view)
    }

    @Test
    fun `json text is pretty printed and flagged as json`() {
        // Arrange
        val bytes = """{"b":1,"a":[true]}""".encodeToByteArray()

        // Act
        val view = classifyValue(bytes) as KvValueView.Text

        // Assert
        assertTrue(view.isJson)
        assertTrue(view.text.contains("\n"))
        assertTrue(view.text.contains("\"a\""))
    }

    @Test
    fun `invalid utf-8 is shown as binary with a hex preview and the size`() {
        // Arrange
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x41)

        // Act
        val view = classifyValue(bytes) as KvValueView.Binary

        // Assert
        assertEquals(4L, view.size)
        assertEquals("ff fe 00 41", view.hexPreview)
        assertFalse(view.truncated)
    }

    @Test
    fun `text with a nul byte is treated as binary`() {
        // Arrange
        val bytes = "a\u0000b".encodeToByteArray()

        // Act
        val view = classifyValue(bytes)

        // Assert
        assertTrue(view is KvValueView.Binary)
    }

    @Test
    fun `binary preview is capped and marked truncated`() {
        // Arrange
        val bytes = ByteArray(KV_HEX_PREVIEW_BYTES + 10) { 0xFF.toByte() }

        // Act
        val view = classifyValue(bytes) as KvValueView.Binary

        // Assert
        assertTrue(view.truncated)
        assertEquals(KV_HEX_PREVIEW_BYTES, view.previewBytes)
        assertEquals(KV_HEX_PREVIEW_BYTES * 3 - 1, view.hexPreview.length)
    }

    @Test
    fun `text that only looks like json but does not parse stays plain text`() {
        // Arrange
        val bytes = "{not json".encodeToByteArray()

        // Act
        val view = classifyValue(bytes) as KvValueView.Text

        // Assert
        assertFalse(view.isJson)
        assertEquals("{not json", view.text)
    }
}
