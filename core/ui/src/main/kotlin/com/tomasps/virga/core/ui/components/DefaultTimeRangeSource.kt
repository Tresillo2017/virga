package com.tomasps.virga.core.ui.components

import kotlinx.coroutines.flow.Flow

/** The analytics window a metrics screen opens on, chosen in Settings. Features read it; the account feature owns it. */
interface DefaultTimeRangeSource {
    val defaultRange: Flow<TimeRange>
}
