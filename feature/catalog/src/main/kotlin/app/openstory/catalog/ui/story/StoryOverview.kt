package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.content.HikariMetadataGroup
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun StoryOverview(
    story: StoryUiModel,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    if (onRefresh == null) {
        StoryOverviewList(story, compact, modifier.fillMaxWidth())
        return
    }
    HikariPullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth().testTag("story-overview-pull-refresh"),
    ) {
        StoryOverviewList(story, compact, Modifier.fillMaxSize())
    }
}

@Composable
private fun StoryOverviewList(
    story: StoryUiModel,
    compact: Boolean,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        item(key = "story-overview-header") {
            HikariSectionHeader(title = "Details")
        }
        story.description?.takeIf(String::isNotBlank)?.let { description ->
            item {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (compact) COMPACT_DESCRIPTION_LINES else Int.MAX_VALUE,
                )
            }
        }
        item { HikariMetadataGroup("Authors", story.authors) }
        item { HikariMetadataGroup("Genres", story.genres) }
        item { HikariMetadataGroup("Languages", story.languageTags) }
        item { HikariMetadataGroup("Also known as", story.aliases) }
    }
}

private const val COMPACT_DESCRIPTION_LINES = 7
