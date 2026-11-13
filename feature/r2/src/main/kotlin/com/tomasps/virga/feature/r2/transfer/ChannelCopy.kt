package com.tomasps.virga.feature.r2.transfer

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import java.io.InputStream
import java.io.OutputStream

private const val COPY_BUFFER_BYTES = 64 * 1024
private const val END_OF_STREAM = -1

/**
 * Copies a response body into [output] in fixed-size chunks, reporting the running total after each chunk. Stops
 * promptly when the coroutine is cancelled, because every read is a suspension point.
 */
internal suspend fun copyFromChannel(
    channel: ByteReadChannel,
    output: OutputStream,
    onProgress: (Long) -> Unit,
) {
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var total = 0L
    while (true) {
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read == END_OF_STREAM) break
        output.write(buffer, 0, read)
        total += read
        onProgress(total)
    }
    output.flush()
}

/**
 * Copies [input] into a request body, reporting the running total. Flushes the channel at the end and returns the
 * number of bytes copied, so the caller can compare it with the length it declared.
 */
internal suspend fun copyToChannel(
    input: InputStream,
    channel: ByteWriteChannel,
    onProgress: (Long) -> Unit,
): Long {
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var total = 0L
    while (true) {
        val read = input.read(buffer, 0, buffer.size)
        if (read == END_OF_STREAM) break
        channel.writeFully(buffer, 0, read)
        total += read
        onProgress(total)
    }
    channel.flush()
    return total
}
