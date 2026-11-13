package com.tomasps.virga.feature.kv.files

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the documents the Storage Access Framework returns. ViewModels see only this interface and string
 * URIs, so they can be tested without Android. A null result means the document could not be read.
 */
interface KvFileStore {
    suspend fun sizeOf(uri: String): Long?

    /** The file's display name, or null when the provider does not give one. */
    suspend fun displayNameOf(uri: String): String?

    /** Reads at most [maxBytes]. Returns null when the document is larger, without reading it all. */
    suspend fun readBytes(uri: String, maxBytes: Long): ByteArray?

    /** Writes [bytes] over the document. Returns false when the write failed. */
    suspend fun writeBytes(uri: String, bytes: ByteArray): Boolean
}

/** [KvFileStore] over the app's ContentResolver. */
class ContentResolverKvFileStore(private val context: Context) : KvFileStore {

    override suspend fun sizeOf(uri: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }
        }.getOrNull()
    }

    override suspend fun displayNameOf(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null }
        }.getOrNull()
    }

    override suspend fun readBytes(uri: String, maxBytes: Long): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream -> readAtMost(stream, maxBytes) }
        }.getOrNull()
    }

    override suspend fun writeBytes(uri: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(bytes) } != null
        }.getOrDefault(false)
    }
}

private const val COPY_BUFFER_BYTES = 8 * 1024

/**
 * Reads the stream until its end or until one byte past [maxBytes], then returns null if it held more than [maxBytes].
 * Uses a plain read loop, because InputStream.readNBytes is only available on Android 13 and later.
 */
internal fun readAtMost(stream: InputStream, maxBytes: Long): ByteArray? {
    val limit = maxBytes + 1
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var total = 0L
    while (total < limit) {
        val wanted = minOf(buffer.size.toLong(), limit - total).toInt()
        val count = stream.read(buffer, 0, wanted)
        if (count < 0) break
        out.write(buffer, 0, count)
        total += count
    }
    return out.toByteArray().takeIf { it.size <= maxBytes }
}
