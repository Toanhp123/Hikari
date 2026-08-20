package app.openstory.catalog.ui.discover

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead

internal fun LazyListScope.discoverTopRatedItems(
    items: List<DiscoverStoryItem>,
    onSelected: (StoryId) -> Unit,
    separatedFromPreviousSection: Boolean,
) {
    val visible = items.take(MAX_TOP_RATED_ITEMS)
    val first = visible.firstOrNull() ?: return

    item(
        key = "discover-top-rated-lead:${first.storyId.value}",
        contentType = TOP_RATED_LEAD_CONTENT_TYPE,
    ) {
        HikariSectionLead(
            modifier = Modifier.testTag("discover-top-rated"),
            separatedFromPreviousSection = separatedFromPreviousSection,
            header = { HikariSectionHeader(title = "TOP RATED") },
            firstContent = {
                DiscoverTopRatedRow(
                    rank = 1,
                    item = first,
                    onSelected = onSelected,
                )
            },
        )
    }
    itemsIndexed(
        items = visible.drop(1),
        key = { _, item -> "discover-top-rated:${item.storyId.value}" },
        contentType = { _, _ -> TOP_RATED_ROW_CONTENT_TYPE },
    ) { index, item ->
        DiscoverTopRatedRow(
            rank = index + 2,
            item = item,
            onSelected = onSelected,
        )
    }
}

private const val MAX_TOP_RATED_ITEMS = 5
private const val TOP_RATED_LEAD_CONTENT_TYPE = "discover-top-rated-lead"
private const val TOP_RATED_ROW_CONTENT_TYPE = "discover-top-rated-row"
