package com.tomasps.virga.core.common.result

/** Pagination metadata from result_info. Every field is nullable because beta endpoints omit some. */
sealed interface PageInfo {
    val perPage: Int?
    val count: Int?

    data class Page(
        val page: Int?,
        override val perPage: Int?,
        override val count: Int?,
        val totalCount: Int?,
    ) : PageInfo

    data class Cursor(
        val nextCursor: String?,
        override val perPage: Int?,
        override val count: Int?,
    ) : PageInfo
}
