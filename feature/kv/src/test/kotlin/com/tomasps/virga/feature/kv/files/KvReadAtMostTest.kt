package com.tomasps.virga.feature.kv.files

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KvReadAtMostTest {

    @Test
    fun `a stream exactly at the limit is read in full`() {
        // Arrange
        val data = ByteArray(LIMIT) { it.toByte() }

        // Act
        val read = readAtMost(ByteArrayInputStream(data), LIMIT.toLong())

        // Assert
        assertArrayEquals(data, read)
    }

    @Test
    fun `a stream one byte over the limit is refused`() {
        // Arrange
        val data = ByteArray(LIMIT + 1)

        // Act
        val read = readAtMost(ByteArrayInputStream(data), LIMIT.toLong())

        // Assert
        assertNull(read)
    }

    @Test
    fun `a stream far larger than the limit stops reading after one byte past it`() {
        // Arrange
        val source = CountingStream(ByteArray(LIMIT * 10))

        // Act
        val read = readAtMost(source, LIMIT.toLong())

        // Assert
        assertNull(read)
        assertArrayEquals(IntArray(1) { LIMIT + 1 }, intArrayOf(source.bytesRead))
    }

    @Test
    fun `an empty stream reads as an empty array`() {
        // Act
        val read = readAtMost(ByteArrayInputStream(ByteArray(0)), LIMIT.toLong())

        // Assert
        assertArrayEquals(ByteArray(0), read)
    }

    /** Counts the bytes handed out, so a test can show the reader stopped early. */
    private class CountingStream(private val data: ByteArray) : java.io.InputStream() {
        private var position = 0
        var bytesRead = 0
            private set

        override fun read(): Int {
            if (position >= data.size) return -1
            bytesRead++
            return data[position++].toInt()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(length, data.size - position)
            System.arraycopy(data, position, buffer, offset, count)
            position += count
            bytesRead += count
            return count
        }
    }

    private companion object {
        const val LIMIT = 100
    }
}
