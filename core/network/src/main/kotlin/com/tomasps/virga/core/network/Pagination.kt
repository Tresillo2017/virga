package com.tomasps.virga.core.network

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.PageInfo
import com.tomasps.virga.core.common.result.getOrNull
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.DeserializationStrategy

private const val FIRST_PAGE = 1
private const val TRUNCATED_REASON = "List has more pages than the safety bound"

/** Where the next request starts: a page number (page style) or an opaque cursor (cursor style). */
internal data class PagePosition(val page: Int = FIRST_PAGE, val cursor: String? = null) {
    fun toQuery(perPage: Int): QueryParams = if (cursor != null) {
        mapOf("cursor" to cursor, "per_page" to perPage.toString())
    } else {
        mapOf("page" to page.toString(), "per_page" to perPage.toString())
    }
}

/**
 * Emits one result per page, detecting the style from each response's result_info. Stops after a failure or
 * after the last page. If [CloudflareEndpoints.MAX_PAGES] pages were read and more remain, the flow ends with a
 * [ApiError.Local] failure instead of stopping silently.
 */
fun <T> CloudflareApi.pagedFlow(
    path: String,
    query: QueryParams = emptyMap(),
    deserializer: DeserializationStrategy<List<T>>,
    perPage: Int = CloudflareEndpoints.DEFAULT_PER_PAGE,
): Flow<ApiResult<List<T>>> = flow {
    var position = PagePosition()
    repeat(CloudflareEndpoints.MAX_PAGES) {
        val result = execute(HttpMethod.Get, path, query + position.toQuery(perPage), null, deserializer)
        emit(result)
        val success = result as? ApiResult.Success ?: return@flow
        position = nextPagePosition(success.pageInfo, success.value.size, position, perPage) ?: return@flow
    }
    emit(ApiResult.Failure(ApiError.Local(TRUNCATED_REASON)))
}

/** Collects every page. The first failure wins and discards the pages gathered so far, including a truncation. */
suspend fun <T> CloudflareApi.allPages(
    path: String,
    query: QueryParams = emptyMap(),
    deserializer: DeserializationStrategy<List<T>>,
    perPage: Int = CloudflareEndpoints.DEFAULT_PER_PAGE,
): ApiResult<List<T>> {
    val pages = pagedFlow(path, query, deserializer, perPage).toList()
    val failure = pages.firstNotNullOfOrNull { it as? ApiResult.Failure }
    return failure ?: ApiResult.Success(pages.flatMap { it.getOrNull().orEmpty() })
}

internal fun nextPagePosition(
    info: PageInfo?,
    itemCount: Int,
    current: PagePosition,
    perPage: Int,
): PagePosition? {
    if (itemCount == 0) return null
    return when (info) {
        is PageInfo.Cursor -> info.nextCursor?.let { PagePosition(cursor = it) }
        is PageInfo.Page -> nextPageNumber(info, itemCount, current, perPage)
        null -> null
    }
}

/**
 * Page style. The server may cap per_page below what was asked, so the size it reports wins. With a total,
 * the walk stops once the pages read cover it; without one, a short page ends the walk.
 */
private fun nextPageNumber(info: PageInfo.Page, itemCount: Int, current: PagePosition, perPage: Int): PagePosition? {
    val pageSize = info.perPage?.takeIf { it > 0 } ?: perPage
    val total = info.totalCount
    val hasMore = if (total != null) current.page * pageSize < total else itemCount >= pageSize
    return if (hasMore) PagePosition(page = current.page + 1) else null
}
