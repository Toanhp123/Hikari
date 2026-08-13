package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingSheet
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.common.id.PluginId

@Composable
internal fun StorySources(
    story: StoryUiModel,
    selectedSource: StorySourceIdentity?,
    refreshing: Boolean,
    failure: StoryRefreshFailure?,
    onRetry: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Catalog sources", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = onRetry,
                    enabled = !refreshing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("story-source-refresh"),
                ) {
                    Text("Refresh details")
                }
                if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                failure?.let {
                    Text(
                        "Source detail refresh failed: ${it.code}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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

@Composable
private fun SourceCard(source: CatalogEntry, selected: Boolean, onSelected: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${source.title}, source ${source.pluginId.value}"
        }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected, onSelected, { Text(source.pluginId.value) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("story-source-${source.pluginId.value}-${source.sourceId}"),
        )
        Text(source.title, style = MaterialTheme.typography.titleMedium)
        source.description?.let { Text(it, maxLines = 4) }
        source.sourceUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun StorySourceIdentity.matches(entry: CatalogEntry) = pluginId == entry.pluginId && sourceId == entry.sourceId
