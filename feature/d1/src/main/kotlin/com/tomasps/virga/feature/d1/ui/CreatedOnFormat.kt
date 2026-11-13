package com.tomasps.virga.feature.d1.ui

import com.tomasps.virga.core.common.formatIsoDate
import java.time.ZoneId

/** Creation timestamp of a database as a readable date. See [formatIsoDate]. */
internal fun formatCreatedOn(raw: String?, zone: ZoneId = ZoneId.systemDefault()): String? = formatIsoDate(raw, zone)
