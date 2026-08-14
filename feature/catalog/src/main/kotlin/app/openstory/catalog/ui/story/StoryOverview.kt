package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        item { MetadataGroup("Authors", story.authors) }
        item { MetadataGroup("Genres", story.genres) }
        item { MetadataGroup("Languages", story.languageTags) }
        item { MetadataGroup("Also known as", story.aliases) }
    }
}

private const val COMPACT_DESCRIPTION_LINES = 7

@Composable
private fun MetadataGroup(title: String, values: Set<String>) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space6)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        values.sorted().forEach { value ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(end = MaterialTheme.hikariSpacing.space6),
            ) {
                Text(
                    value,
                    Modifier.padding(
                        horizontal = MaterialTheme.hikariSpacing.space12,
                        vertical = MaterialTheme.hikariSpacing.space7,
                    ),
                )
            }
        }
    }
}
