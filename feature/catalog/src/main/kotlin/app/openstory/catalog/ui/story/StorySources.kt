package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingSheet
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.common.id.PluginId
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun StorySources(
    story: StoryUiModel,
    selectedSource: StorySourceIdentity?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    modifier: Modifier = Modifier,
) {
    HikariPullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth().testTag("story-sources-pull-refresh"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        ) {
            item(key = "story-sources-header") {
                Column(
                    modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space16),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
                ) {
                    HikariSectionHeader(title = "Sources")
                }
            }
            items(story.sources, key = { "${it.pluginId.value}:${it.sourceId}" }) { source ->
                SourceCard(source, selectedSource?.matches(source) == true) {
                    onSourceSelected(source.pluginId, source.sourceId)
                }
            }
            mappingState?.let { item { MappingSheet(it, mappingActions) } }
        }
    }
}

@Composable
private fun SourceCard(source: CatalogEntry, selected: Boolean, onSelected: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${source.title}, source ${source.pluginId.value}"
            }
            .padding(
                horizontal = MaterialTheme.hikariSpacing.space16,
                vertical = MaterialTheme.hikariSpacing.space8,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space6),
    ) {
        HikariFilterChip(
            selected = selected,
            onClick = onSelected,
            label = { Text(source.pluginId.value) },
            modifier = Modifier.testTag("story-source-${source.pluginId.value}-${source.sourceId}"),
        )
        Text(source.title, style = MaterialTheme.typography.titleMedium)
        source.description?.let { Text(it, maxLines = 4) }
        source.sourceUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun StorySourceIdentity.matches(source: CatalogEntry): Boolean =
    pluginId == source.pluginId && sourceId == source.sourceId
