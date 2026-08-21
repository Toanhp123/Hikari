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
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.catalog.ui.mapping.mappingItems
import app.openstory.common.id.PluginId
import app.openstory.designsystem.content.HikariMetadataBadgeGroup
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.control.HikariCompactAction
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
    onPinPrimary: (PluginId, String) -> Unit,
    onUseAutomaticPrimary: () -> Unit,
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
            when {
                firstSource == null -> item(key = "story-sources-header") {
                    StorySourcesHeader(story.preferenceMode)
                }
                story.preferenceMode == CanonicalSourcePreferenceMode.PINNED -> {
                    item(key = "story-sources-header") {
                        HikariSectionLead(
                            header = { StorySourcesHeader(story.preferenceMode) },
                            firstContent = {
                                HikariCompactAction(
                                    onClick = onUseAutomaticPrimary,
                                    modifier = Modifier.fillMaxWidth().testTag("story-source-use-automatic"),
                                    contentDescription = "Return to automatic primary source selection",
                                ) {
                                    Text("Use automatic primary")
                                }
                            },
                        )
                    }
                    items(story.sources, key = { "${it.pluginId.value}:${it.sourceId}" }) { source ->
                        SourceCard(
                            source = source,
                            selected = selectedSource?.matches(source) == true,
                            effectivePrimary = story.effectivePrimary.matches(source),
                            pinned = story.pinnedSource.matches(source),
                            onSelected = { onSourceSelected(source.pluginId, source.sourceId) },
                            onPin = { onPinPrimary(source.pluginId, source.sourceId) },
                        )
                    }
                }
                else -> {
                    item(key = "story-sources-header") {
                        HikariSectionLead(
                            header = { StorySourcesHeader(story.preferenceMode) },
                            firstContent = {
                                SourceCard(
                                    source = firstSource,
                                    selected = selectedSource?.matches(firstSource) == true,
                                    effectivePrimary = story.effectivePrimary.matches(firstSource),
                                    pinned = false,
                                    onSelected = { onSourceSelected(firstSource.pluginId, firstSource.sourceId) },
                                    onPin = { onPinPrimary(firstSource.pluginId, firstSource.sourceId) },
                                )
                            },
                        )
                    }
                    items(story.sources.drop(1), key = { "${it.pluginId.value}:${it.sourceId}" }) { source ->
                        SourceCard(
                            source = source,
                            selected = selectedSource?.matches(source) == true,
                            effectivePrimary = story.effectivePrimary.matches(source),
                            pinned = false,
                            onSelected = { onSourceSelected(source.pluginId, source.sourceId) },
                            onPin = { onPinPrimary(source.pluginId, source.sourceId) },
                        )
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
private fun StorySourcesHeader(mode: CanonicalSourcePreferenceMode) {
    HikariSectionHeader(
        title = "Sources",
        subtitle = if (mode == CanonicalSourcePreferenceMode.AUTO) {
            "Automatic canonical presentation; select a row to inspect raw provider facts."
        } else {
            "Pinned primary with field-specific canonical fusion; raw sources remain inspectable."
        },
    )
}

@Composable
private fun SourceCard(
    source: CatalogEntry,
    selected: Boolean,
    effectivePrimary: Boolean,
    pinned: Boolean,
    onSelected: () -> Unit,
    onPin: () -> Unit,
) {
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
                    if (selected) add("Inspecting")
                    if (effectivePrimary) add("Effective primary")
                    if (pinned) add("Pinned")
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
            if (!pinned) {
                HikariCompactAction(
                    onClick = onPin,
                    modifier = Modifier.testTag("story-source-pin-${source.pluginId.value}-${source.sourceId}"),
                    contentDescription = "Pin ${source.pluginId.value} as primary source",
                ) {
                    Text("Use as primary")
                }
            }
        }
    }
}

private fun SourceKey?.matches(source: CatalogEntry): Boolean =
    this?.let { it.pluginId == source.pluginId && it.sourceId == source.sourceId } == true

private fun StorySourceIdentity.matches(source: CatalogEntry): Boolean =
    pluginId == source.pluginId && sourceId == source.sourceId
