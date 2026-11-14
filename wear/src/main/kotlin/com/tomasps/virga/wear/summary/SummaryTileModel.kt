package com.tomasps.virga.wear.summary

import com.tomasps.virga.wear.present.ageText
import java.time.Instant

enum class SummaryTileKind { Numbers, NotConnected, Reconnect, MissingAccess, NoData }

/** What the tile draws. [requests] and [errors] are compact text; [age] is set only for stale numbers. */
data class SummaryTileModel(
    val kind: SummaryTileKind,
    val requests: String = "",
    val errors: String = "",
    val age: String? = null,
    val isStale: Boolean = false,
)

fun summaryTileFor(surface: SummarySurface, now: Instant): SummaryTileModel = when (surface.status) {
    SummaryStatus.NotConnected -> SummaryTileModel(SummaryTileKind.NotConnected)
    SummaryStatus.Reconnect -> SummaryTileModel(SummaryTileKind.Reconnect)
    SummaryStatus.MissingAnalyticsAccess -> SummaryTileModel(SummaryTileKind.MissingAccess)
    SummaryStatus.Fresh -> numbers(surface, age = null, isStale = false)
    SummaryStatus.Stale -> numbers(surface, age = surface.fetchedAt?.let { ageText(it, now) }, isStale = true)
}

private fun numbers(surface: SummarySurface, age: String?, isStale: Boolean): SummaryTileModel {
    val data = surface.data ?: return SummaryTileModel(SummaryTileKind.NoData)
    return SummaryTileModel(
        kind = SummaryTileKind.Numbers,
        requests = compactCount(data.requests),
        errors = compactCount(data.errors),
        age = age,
        isStale = isStale,
    )
}
