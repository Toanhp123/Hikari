package app.openstory.catalog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariBreakpoints
import app.openstory.designsystem.theme.hikariDimensions

@Composable
fun StoryShelf(
    title: String,
    entries: List<CatalogEntry>,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val cardWidth = when {
            maxWidth < MaterialTheme.hikariBreakpoints.narrowContent ->
                MaterialTheme.hikariDimensions.posterShelfNarrowWidth
            maxWidth < MaterialTheme.hikariBreakpoints.expandedContent ->
                MaterialTheme.hikariDimensions.posterShelfWidth
            else -> MaterialTheme.hikariDimensions.posterShelfWideWidth
        }
        androidx.compose.foundation.layout.Column {
            HikariSectionHeader(
                title = title,
                modifier = Modifier.semantics { heading() },
            )
            LazyRow(
                contentPadding = PaddingValues(vertical = MaterialTheme.hikariSpacing.space8),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
            ) {
                items(entries, key = { "${it.pluginId.value}:${it.sourceId}" }) { entry ->
                    StoryCoverCard(entry, title, onSelected, cardWidth)
                }
            }
        }
    }
}
