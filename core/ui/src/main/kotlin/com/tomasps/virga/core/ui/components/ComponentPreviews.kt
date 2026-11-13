package com.tomasps.virga.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.theme.VirgaTheme

@Preview(name = "Stat tiles, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun StatTilePreview() {
    VirgaTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            StatTile(
                label = "Requests, 24h",
                value = "1,284,905",
                delta = StatDelta("+12.4% vs previous day", DeltaTone.Positive),
                sparkline = listOf(3.0, 5.0, 4.0, 7.0, 6.0, 9.0),
            )
            Column(Modifier.padding(top = 12.dp)) {
                StatTile(label = "Cache hit rate", value = "14.10%", delta = StatDelta("-0.8 pts", DeltaTone.Negative))
            }
        }
    }
}

@Preview(name = "Rows and status", showBackground = true)
@Composable
private fun RowsPreview() {
    VirgaTheme(darkTheme = true) {
        Column {
            SectionHeader(title = "Workers", action = { Text("See all") })
            ListRow(
                headline = "api-gateway",
                supporting = "Deployed 2 hours ago",
                leading = { StatusDot(StatusTone.Success) },
                onClick = {},
            )
            ListRow(headline = "image-resize", supporting = "Build running", leading = { StatusDot(StatusTone.Pending) })
            KeyValueRow(key = "Compatibility date", value = "2026-09-01", monospaceValue = true)
            OpenInDashboardRow(description = "Issues are not available in the API.", onOpen = {})
        }
    }
}

@Preview(name = "Code block", showBackground = true)
@Composable
private fun CodeBlockPreview() {
    VirgaTheme(darkTheme = true) {
        CodeBlock(
            code = "{\"success\": true, \"result\": {\"name\": \"api-gateway\", \"routes\": [\"example.com/*\"]}}",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Time range and search", showBackground = true)
@Composable
private fun TimeRangeSearchPreview() {
    VirgaTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            TimeRangePicker(selected = TimeRange.TWENTY_FOUR_HOURS, onSelected = {})
            Column(Modifier.padding(top = 12.dp)) {
                SearchFieldPreviewBody()
            }
        }
    }
}

@Composable
private fun SearchFieldPreviewBody() {
    var query by remember { mutableStateOf("api") }
    SearchField(query = query, onQueryChange = { query = it })
}
