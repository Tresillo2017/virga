package com.tomasps.virga.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tomasps.virga.core.ui.R

/** Analytics windows offered on every metrics screen. [hours] is the window length. */
enum class TimeRange(val labelResId: Int, val hours: Int) {
    LAST_HOUR(R.string.core_ui_range_1h, 1),
    SIX_HOURS(R.string.core_ui_range_6h, 6),
    TWENTY_FOUR_HOURS(R.string.core_ui_range_24h, 24),
    SEVEN_DAYS(R.string.core_ui_range_7d, 7 * 24),
    THIRTY_DAYS(R.string.core_ui_range_30d, 30 * 24);

    /** Window length in seconds, the unit most analytics queries take. */
    val seconds: Long get() = hours * SECONDS_PER_HOUR

    private companion object {
        const val SECONDS_PER_HOUR = 3_600L
    }
}

/** Segmented control for choosing a [TimeRange]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangePicker(
    selected: TimeRange,
    onSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
    options: List<TimeRange> = TimeRange.entries,
) {
    val description = stringResource(R.string.core_ui_range_description)
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        options.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelected(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    text = stringResource(range.labelResId),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
