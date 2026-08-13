package app.openstory.catalog.ui.components

import androidx.compose.foundation.layout.Arrangement
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

@Composable
fun StoryShelf(
    title: String,
    entries: List<CatalogEntry>,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier) {
        HikariSectionHeader(
            title = title,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(
            contentPadding = PaddingValues(vertical = MaterialTheme.hikariSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.medium),
        ) {
            items(entries, key = { "${it.pluginId.value}:${it.sourceId}" }) { entry ->
                StoryCoverCard(entry, title, onSelected)
            }
        }
    }
}
