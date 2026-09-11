package app.openstory.catalog.feature.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun StoryMetadataSections(
    detail: StoryDetailUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space24),
    ) {
        detail.description?.let { description ->
            MetadataSection("About") {
                Text(description, style = MaterialTheme.typography.bodyLarge)
            }
        }
        MetadataValues("Authors", detail.authors)
        MetadataValues("Artists", detail.artists)
        MetadataValues("Genres", detail.genres)
        if (detail.publicationStatus != null || detail.language != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space24),
            ) {
                detail.publicationStatus?.let { status ->
                    MetadataSection("Status", Modifier.weight(1f)) {
                        Text(status, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                detail.language?.let { language ->
                    MetadataSection("Language", Modifier.weight(1f)) {
                        Text(language, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataValues(title: String, values: List<String>) {
    if (values.isEmpty()) return
    MetadataSection(title) {
        Text(
            text = values.joinToString(separator = " / "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetadataSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        HikariSectionHeader(title)
        content()
    }
}
