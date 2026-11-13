package com.tomasps.virga.feature.r2.transfer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Name, size and type of a document the user picked. [size] is null when the provider does not report it. */
data class DocumentInfo(
    val displayName: String?,
    val size: Long?,
    val mimeType: String?,
)

/**
 * Byte access to documents the user chose through the Storage Access Framework. Uris are strings so the
 * transfer logic stays free of Android types and can be tested on the JVM.
 */
interface R2DocumentStreams {
    /** Throws [IOException] when the document cannot be opened. */
    fun openForRead(uriString: String): InputStream

    /** Throws [IOException] when the document cannot be opened for writing. */
    fun openForWrite(uriString: String): OutputStream

    fun describe(uriString: String): DocumentInfo

    /** Best effort removal of a partial download. Returns false when the provider refuses. */
    fun delete(uriString: String): Boolean
}

/** Real implementation over the app's content resolver. */
class AndroidR2DocumentStreams(private val context: Context) : R2DocumentStreams {

    override fun openForRead(uriString: String): InputStream =
        context.contentResolver.openInputStream(Uri.parse(uriString))
            ?: throw IOException("The source document could not be opened")

    override fun openForWrite(uriString: String): OutputStream =
        context.contentResolver.openOutputStream(Uri.parse(uriString), WRITE_MODE)
            ?: throw IOException("The destination document could not be opened")

    override fun describe(uriString: String): DocumentInfo {
        val uri = Uri.parse(uriString)
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                DocumentInfo(
                    displayName = cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME),
                    size = cursor.getLongOrNull(OpenableColumns.SIZE),
                    mimeType = null,
                )
            } else {
                null
            }
        }
        return DocumentInfo(
            displayName = row?.displayName,
            size = row?.size,
            mimeType = context.contentResolver.getType(uri),
        )
    }

    override fun delete(uriString: String): Boolean = runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uriString))
    }.getOrDefault(false)

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.getLongOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private companion object {
        const val WRITE_MODE = "w"
    }
}
