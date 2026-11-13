package com.tomasps.virga.feature.domains.dns

import android.content.Context
import android.net.Uri
import com.tomasps.virga.core.common.DispatcherProvider
import com.tomasps.virga.core.common.StandardDispatcherProvider
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Outcome of reading a BIND file picked by the owner. */
sealed interface BindFileRead {
    data class Content(val bytes: ByteArray) : BindFileRead
    data object TooLarge : BindFileRead
    data object Unreadable : BindFileRead
}

/** A zone file larger than this is refused. A generous ceiling: real BIND zones for one domain are a few kilobytes. */
const val MAX_BIND_FILE_BYTES = 1_048_576

/**
 * Reads [open] at most [maxBytes] + 1 bytes, so an oversized file never fills memory. Returns [BindFileRead.Unreadable]
 * when there is no stream or the read fails.
 */
fun readBoundedBind(open: () -> InputStream?, maxBytes: Int = MAX_BIND_FILE_BYTES): BindFileRead = try {
    val bytes = open()?.use { readAtMost(it, maxBytes + 1) }
    when {
        bytes == null -> BindFileRead.Unreadable
        bytes.size > maxBytes -> BindFileRead.TooLarge
        else -> BindFileRead.Content(bytes)
    }
} catch (ignored: IOException) {
    BindFileRead.Unreadable
}

/** Reads up to [limit] bytes. Stops at end of stream, so it never allocates more than [limit] for one read. */
internal fun readAtMost(stream: InputStream, limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(READ_CHUNK_BYTES)
    while (out.size() < limit) {
        val count = stream.read(chunk, 0, minOf(chunk.size, limit - out.size()))
        if (count < 0) break
        out.write(chunk, 0, count)
    }
    return out.toByteArray()
}

private const val READ_CHUNK_BYTES = 8_192

/** File IO for BIND import and export. Both run on the IO dispatcher, never on the main thread. */
class BindFileIo(private val dispatchers: DispatcherProvider = StandardDispatcherProvider()) {

    suspend fun read(context: Context, uri: Uri): BindFileRead = withContext(dispatchers.io) {
        readBoundedBind({ context.contentResolver.openInputStream(uri) })
    }

    /** Writes [text] as UTF-8. Returns false when the stream cannot be opened or the write fails. */
    suspend fun write(context: Context, uri: Uri, text: String): Boolean = withContext(dispatchers.io) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
        } catch (ignored: IOException) {
            false
        }
    }
}
