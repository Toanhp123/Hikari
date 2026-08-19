package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun DiscoverLatestGrid(
    items: List<DiscoverStoryItem>,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = items.take(MAX_LATEST_ITEMS)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover-latest-grid"),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
    ) {
        visible.chunked(LATEST_COLUMNS).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
            ) {
                rowItems.forEach { item ->
                    DiscoverLatestCard(
                        item = item,
                        onSelected = onSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(LATEST_COLUMNS - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private const val LATEST_COLUMNS = 3
private const val MAX_LATEST_ITEMS = 9
