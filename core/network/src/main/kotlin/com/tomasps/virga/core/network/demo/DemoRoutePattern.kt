package com.tomasps.virga.core.network.demo

import io.ktor.http.HttpMethod
import io.ktor.http.decodeURLPart

typealias DemoHandler = suspend (DemoRequest) -> DemoReply

private const val CATCH_ALL_SUFFIX = "...}"
private const val SHAPE_PARAM = "{}"

/** One registered route. `{name}` matches one segment; `{name...}` matches the rest of the path. */
internal class DemoRoutePattern(
    val method: HttpMethod,
    val pattern: String,
    val handler: DemoHandler,
) {
    private val segments: List<String> = splitPath(pattern)

    /**
     * The pattern with every parameter name erased, so `/accounts/{id}` and `/accounts/{accountId}` have the
     * same shape. Two routes with one method and one shape can never be told apart, so registration rejects them.
     */
    val shape: String = segments.joinToString("/") { if (it.isParam() || it.isCatchAll()) SHAPE_PARAM else it }

    /** Returns decoded path parameters when [requestMethod] and the encoded [encodedPath] match, else null. */
    fun match(requestMethod: HttpMethod, encodedPath: String): Map<String, String>? {
        if (requestMethod != method) return null
        val actual = splitPath(encodedPath)
        val params = mutableMapOf<String, String>()
        for ((index, segment) in segments.withIndex()) {
            if (segment.isCatchAll()) {
                if (actual.size <= index) return null
                params[segment.paramName()] = actual.drop(index).joinToString("/") { it.decodeURLPart() }
                return params
            }
            val actualSegment = actual.getOrNull(index) ?: return null
            if (segment.isParam()) {
                params[segment.paramName()] = actualSegment.decodeURLPart()
            } else if (segment != actualSegment.decodeURLPart()) {
                return null
            }
        }
        return if (actual.size == segments.size) params else null
    }

    private fun String.isParam(): Boolean = startsWith("{") && endsWith("}")

    private fun String.isCatchAll(): Boolean = startsWith("{") && endsWith(CATCH_ALL_SUFFIX)

    private fun String.paramName(): String = removePrefix("{").removeSuffix(if (isCatchAll()) CATCH_ALL_SUFFIX else "}")

    private companion object {
        fun splitPath(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }
    }
}
