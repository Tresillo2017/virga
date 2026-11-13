package com.tomasps.virga.feature.domains.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class BindFileIoTest {

    @Test
    fun `a file within the limit is read in full`() {
        // Arrange
        val text = "example.com. 300 IN A 192.0.2.1\n".toByteArray()

        // Act
        val read = readBoundedBind({ ByteArrayInputStream(text) }, maxBytes = 1024)

        // Assert
        assertTrue(read is BindFileRead.Content)
        assertArrayEquals(text, (read as BindFileRead.Content).bytes)
    }

    @Test
    fun `a file exactly at the limit is still accepted`() {
        // Arrange
        val text = ByteArray(16) { 'a'.code.toByte() }

        // Act
        val read = readBoundedBind({ ByteArrayInputStream(text) }, maxBytes = 16)

        // Assert
        assertEquals(16, (read as BindFileRead.Content).bytes.size)
    }

    @Test
    fun `a file one byte over the limit is refused without reading all of it`() {
        // Arrange
        val text = ByteArray(17) { 'a'.code.toByte() }

        // Act
        val read = readBoundedBind({ ByteArrayInputStream(text) }, maxBytes = 16)

        // Assert
        assertEquals(BindFileRead.TooLarge, read)
    }

    @Test
    fun `a picker that returns no stream is unreadable`() {
        // Act
        val read = readBoundedBind({ null })

        // Assert
        assertEquals(BindFileRead.Unreadable, read)
    }

    @Test
    fun `an IO failure while reading is unreadable, not a crash`() {
        // Arrange
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("disk gone")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("disk gone")
        }

        // Act
        val read = readBoundedBind({ failing })

        // Assert
        assertEquals(BindFileRead.Unreadable, read)
    }

    @Test
    fun `the default limit is one megabyte`() {
        assertEquals(1_048_576, MAX_BIND_FILE_BYTES)
    }
}
