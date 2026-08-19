package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.catalog.ui.mapping.mappingItems
import app.openstory.common.id.PluginId
import app.openstory.designsystem.content.HikariMetadataBadgeGroup
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.surface.HikariContentCardStyle
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
            contentPadding = storySectionContentPadding(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
        ) {
            val firstSource = story.sources.firstOrNull()
            if (firstSource == null) {
                item(key = "story-sources-header") { StorySourcesHeader() }
            } else {
                item(key = "story-sources-header") {
                    HikariSectionLead(
                        header = { StorySourcesHeader() },
                        firstContent = {
                            SourceCard(firstSource, selectedSource?.matches(firstSource) == true) {
                                onSourceSelected(firstSource.pluginId, firstSource.sourceId)
                            }
                        },
                    )
                }
                items(story.sources.drop(1), key = { "${it.pluginId.value}:${it.sourceId}" }) { source ->
                    SourceCard(source, selectedSource?.matches(source) == true) {
                        onSourceSelected(source.pluginId, source.sourceId)
                    }
                }
            }
            mappingState?.let { mapping ->
                mappingItems(mapping, mappingActions, separatedFromPreviousSection = true)
            }
        }
    }
}

@Composable
private fun StorySourcesHeader() {
    HikariSectionHeader(
        title = "Sources",
        subtitle = "Choose the catalog entry used for story details.",
    )
}

@Composable
private fun SourceCard(source: CatalogEntry, selected: Boolean, onSelected: () -> Unit) {
    HikariContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("story-source-${source.pluginId.value}-${source.sourceId}")
            .semantics(mergeDescendants = true) {
                contentDescription = "${source.title}, source ${source.pluginId.value}"
                this.selected = selected
            },
        style = if (selected) HikariContentCardStyle.PROMINENT else HikariContentCardStyle.STANDARD,
        onClick = onSelected,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text(source.title, style = MaterialTheme.typography.titleMedium)
            HikariMetadataBadgeGroup(
                buildList {
                    add(source.pluginId.value)
                    if (selected) add("Selected")
                },
            )
            source.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            source.sourceUrl?.takeIf(String::isNotBlank)?.let { url ->
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun StorySourceIdentity.matches(source: CatalogEntry): Boolean =
    pluginId == source.pluginId && sourceId == source.sourceId
