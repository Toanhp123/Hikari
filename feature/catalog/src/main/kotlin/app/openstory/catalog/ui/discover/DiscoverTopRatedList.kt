package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun DiscoverTopRatedList(
    items: List<DiscoverStoryItem>,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover-top-rated"),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
    ) {
        items.take(MAX_TOP_RATED_ITEMS).forEachIndexed { index, item ->
            DiscoverTopRatedRow(
                rank = index + 1,
                item = item,
                onSelected = onSelected,
            )
        }
    }
}

private const val MAX_TOP_RATED_ITEMS = 5
