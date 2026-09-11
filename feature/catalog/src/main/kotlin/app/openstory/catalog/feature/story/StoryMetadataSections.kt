package app.openstory.catalog.feature.story

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun StoryMetadataSections(
    detail: StoryDetailUi,
    modifier: Modifier = Modifier,
) {
    val metadataRows = detail.metadataRows()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space20),
    ) {
        StoryPreviewActions()
        if (metadataRows.isNotEmpty()) StoryMetadataTable(metadataRows)
        if (detail.genres.isNotEmpty()) StoryGenresRow(detail.genres)
        StoryPreviewTabs()
        detail.description?.let { description -> StoryAboutSection(description) }
        StoryRecommendationPreview()
    }
}

@Composable
private fun StoryMetadataTable(rows: List<StoryMetadataRow>) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        ) {
            rows.forEach { row -> MetadataTableRow(row) }
        }
    }
}

@Composable
private fun MetadataTableRow(row: StoryMetadataRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(METADATA_LABEL_WIDTH),
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StoryGenresRow(genres: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
        HikariSectionHeader("Genres")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
            items(genres) { genre ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        text = genre,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.hikariSpacing.space12,
                            vertical = MaterialTheme.hikariSpacing.space4,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryAboutSection(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
        HikariSectionHeader("About")
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (description.length > DESCRIPTION_EXPAND_THRESHOLD) {
            Text(
                text = if (expanded) "Read Less \u2303" else "Read More \u2304",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(vertical = MaterialTheme.hikariSpacing.space4),
            )
        }
    }
}

private fun StoryDetailUi.metadataRows(): List<StoryMetadataRow> = buildList {
    if (authors.isNotEmpty()) add(StoryMetadataRow("Authors", authors.joinToString(" / ")))
    if (artists.isNotEmpty()) add(StoryMetadataRow("Artists", artists.joinToString(" / ")))
    publicationStatus?.let { add(StoryMetadataRow("Status", it)) }
    language?.let { add(StoryMetadataRow("Language", it)) }
}

private data class StoryMetadataRow(val label: String, val value: String)

private val METADATA_LABEL_WIDTH = 96.dp
private const val DESCRIPTION_EXPAND_THRESHOLD = 80
private const val COLLAPSED_DESCRIPTION_LINES = 3
