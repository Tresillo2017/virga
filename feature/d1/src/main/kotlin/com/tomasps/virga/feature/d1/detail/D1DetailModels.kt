package com.tomasps.virga.feature.d1.detail

import androidx.annotation.StringRes
import com.tomasps.virga.feature.d1.R

/** The tabs of a database, in display order. */
enum class D1Tab(@StringRes val labelRes: Int) {
    TABLES(R.string.d1_tab_tables),
    CONSOLE(R.string.d1_tab_console),
    TIME_TRAVEL(R.string.d1_tab_time_travel),
    EXPORT(R.string.d1_tab_export),
    SETTINGS(R.string.d1_tab_settings),
    METRICS(R.string.d1_tab_metrics),
}

/** Read replication modes the API accepts on PUT. */
enum class ReadReplicationMode(val wire: String, @StringRes val labelRes: Int) {
    AUTO("auto", R.string.d1_replication_auto),
    DISABLED("disabled", R.string.d1_replication_disabled),
    ;

    companion object {
        /** Unknown or missing wire values read as disabled, the safe display default. */
        fun fromWire(raw: String?): ReadReplicationMode = entries.firstOrNull { it.wire == raw } ?: DISABLED
    }
}
