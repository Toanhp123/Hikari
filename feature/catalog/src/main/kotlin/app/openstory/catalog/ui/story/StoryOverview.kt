package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.content.HikariMetadataGroup
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun StoryOverview(story: StoryUiModel, compact: Boolean = false, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(MaterialTheme.hikariSpacing.space20),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
    ) {
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
