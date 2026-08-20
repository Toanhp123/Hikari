package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.discoverLatestItems(
    items: List<DiscoverStoryItem>,
    onSelected: (StoryId) -> Unit,
    separatedFromPreviousSection: Boolean,
) {
    val rows = items.take(MAX_LATEST_ITEMS).chunked(LATEST_COLUMNS)
    val firstRow = rows.firstOrNull() ?: return

    item(
        key = latestRowKey(firstRow, lead = true),
        contentType = LATEST_LEAD_CONTENT_TYPE,
    ) {
        HikariSectionLead(
            modifier = Modifier.testTag("discover-latest-grid"),
            separatedFromPreviousSection = separatedFromPreviousSection,
            header = { HikariSectionHeader(title = "LATEST UPDATES") },
            firstContent = { DiscoverLatestRow(firstRow, onSelected) },
        )
    }
    items(
        items = rows.drop(1),
        key = { row -> latestRowKey(row, lead = false) },
        contentType = { LATEST_ROW_CONTENT_TYPE },
    ) { row ->
        DiscoverLatestRow(row, onSelected)
    }
}

@Composable
private fun DiscoverLatestRow(
    items: List<DiscoverStoryItem>,
    onSelected: (StoryId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
    ) {
        items.forEach { item ->
            DiscoverLatestCard(
                item = item,
                onSelected = onSelected,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(LATEST_COLUMNS - items.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun latestRowKey(items: List<DiscoverStoryItem>, lead: Boolean): String = buildString {
    append(if (lead) "discover-latest-lead" else "discover-latest-row")
    items.forEach { item ->
        append(':')
        append(item.storyId.value)
    }
}

private const val LATEST_COLUMNS = 3
private const val MAX_LATEST_ITEMS = 9
private const val LATEST_LEAD_CONTENT_TYPE = "discover-latest-lead"
private const val LATEST_ROW_CONTENT_TYPE = "discover-latest-row"
