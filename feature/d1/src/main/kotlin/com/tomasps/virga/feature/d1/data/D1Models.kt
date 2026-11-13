package com.tomasps.virga.feature.d1.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** One D1 database as the list and detail endpoints return it. Every field is optional: beta shapes vary. */
@Serializable
data class D1Database(
    val uuid: String? = null,
    val name: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val jurisdiction: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("read_replication") val readReplication: ReadReplication? = null,
    val version: String? = null,
)

@Serializable
data class ReadReplication(val mode: String? = null)

/** Body of PUT /d1/database/{id}. The only documented writable field is read_replication.mode. */
@Serializable
data class ReadReplicationUpdate(
    @SerialName("read_replication") val readReplication: ReadReplication,
)

/** Body of POST /d1/database. Jurisdiction and location hint are optional and never sent as null. */
@Serializable
data class CreateDatabaseBody(
    val name: String,
    val jurisdiction: String? = null,
    @SerialName("primary_location_hint") val primaryLocationHint: String? = null,
)

/** Body of POST /d1/database/{id}/raw. Parameters are strings, as the API documents them. */
@Serializable
data class RawRequest(
    val sql: String,
    val params: List<String> = emptyList(),
)

/** One entry of the raw endpoint's result array. Rows arrive as arrays in column order. */
@Serializable
data class RawStatementResult(
    val success: Boolean? = null,
    val results: RawRows? = null,
    val meta: QueryMeta? = null,
)

@Serializable
data class RawRows(
    val columns: List<String> = emptyList(),
    val rows: List<List<JsonElement>> = emptyList(),
)

@Serializable
data class QueryMeta(
    @SerialName("rows_read") val rowsRead: Double? = null,
    @SerialName("rows_written") val rowsWritten: Double? = null,
    val changes: Double? = null,
    val timings: QueryTimings? = null,
)

@Serializable
data class QueryTimings(
    @SerialName("sql_duration_ms") val sqlDurationMs: Double? = null,
)

@Serializable
data class BookmarkResult(val bookmark: String? = null)

@Serializable
data class RestoreResult(
    val bookmark: String? = null,
    val message: String? = null,
    @SerialName("previous_bookmark") val previousBookmark: String? = null,
)

/** Body of POST /d1/database/{id}/export. The first request sends the options; polls send current_bookmark. */
/** No default for [outputFormat]: the shared Json does not encode defaults, so it must be passed explicitly. */
@Serializable
data class ExportRequest(
    @SerialName("output_format") val outputFormat: String,
    @SerialName("current_bookmark") val currentBookmark: String? = null,
    @SerialName("dump_options") val dumpOptions: DumpOptions? = null,
) {
    companion object {
        const val POLLING_FORMAT = "polling"
    }
}

@Serializable
data class DumpOptions(
    val compatibility: String? = null,
    @SerialName("no_data") val noData: Boolean? = null,
    @SerialName("no_schema") val noSchema: Boolean? = null,
)

@Serializable
data class ExportPoll(
    val status: String? = null,
    @SerialName("at_bookmark") val atBookmark: String? = null,
    val result: ExportFile? = null,
    val error: String? = null,
)

/** The finished export. [signedUrl] is a bearer link: never log it, never show it, hand it to DownloadManager only. */
@Serializable
data class ExportFile(
    val filename: String? = null,
    @SerialName("signed_url") val signedUrl: String? = null,
) {
    /** Hides the link: a poll or result that gets logged must not leak a working download credential. */
    override fun toString(): String = "ExportFile(filename=$filename, signedUrl=redacted)"
}

/** A D1 table schema, assembled from the table-valued pragma functions. */
data class TableSchema(
    val name: String,
    val columns: List<ColumnInfo>,
    val indexes: List<IndexInfo>,
    val foreignKeys: List<ForeignKeyInfo>,
) {
    /** Primary key column names in key order, so a composite key keeps its declared order. */
    val primaryKey: List<String>
        get() = columns.filter { it.pkPosition > 0 }.sortedBy { it.pkPosition }.map { it.name }

    /** Row editing needs a key. A table without one is browse-only. */
    val canEditRows: Boolean get() = primaryKey.isNotEmpty()
}

data class ColumnInfo(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String?,
    val pkPosition: Int,
)

data class IndexInfo(
    val name: String,
    val unique: Boolean,
    val columns: List<String>,
)

data class ForeignKeyInfo(
    val fromColumn: String,
    val table: String,
    val toColumn: String?,
    val onDelete: String?,
)
