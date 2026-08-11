package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ui.chapters.ChapterListActions
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.chapters.chapterListItems
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingSheet
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.common.id.PluginId
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun StoryScreen(
    state: StoryUiState,
    onRetry: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    mappingState: MappingUiState? = null,
    mappingActions: MappingActions = MappingActions(),
    chapterState: ChapterListUiState? = null,
    chapterActions: ChapterListActions = ChapterListActions(),
    modifier: Modifier = Modifier,
) {
    val story = state.story
    if (story == null) {
        EmptyStory(state, onRetry, modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.medium),
    ) {
        item(key = "story-header") { StoryHeader(story) }
        item(key = "story-refresh-action") {
            Button(
                onClick = onRetry,
                enabled = !state.refreshing,
                modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
            ) {
                Text("Refresh details")
            }
        }
        if (state.refreshing) {
            item(key = "story-refreshing") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        state.failure?.let { currentFailure ->
            item(key = "story-failure") {
                StoryFailure(currentFailure, onRetry)
            }
        }
        storySourceItems(story.sources, state.selectedSource, onSourceSelected)
        mappingItem(mappingState, mappingActions)
        chapterState?.let { currentState -> chapterListItems(currentState, chapterActions) }
    }
}

private fun LazyListScope.storySourceItems(
    sources: List<CatalogEntry>,
    selectedSource: StorySourceIdentity?,
    onSourceSelected: (PluginId, String) -> Unit,
) {
    items(
        items = sources,
        key = { source -> "${source.pluginId.value}:${source.sourceId}" },
    ) { source ->
        StorySourceCard(
            source = source,
            selected = selectedSource?.matches(source) == true,
            onSelected = { onSourceSelected(source.pluginId, source.sourceId) },
        )
    }
}

private fun LazyListScope.mappingItem(
    state: MappingUiState?,
    actions: MappingActions,
) {
    state?.let { currentState ->
        item(key = "story-content-mapping") {
            MappingSheet(state = currentState, actions = actions)
        }
    }
}

@Composable
private fun EmptyStory(state: StoryUiState, onRetry: () -> Unit, modifier: Modifier) {
    if (state.refreshing) {
        HikariLoadingState(
            label = "Loading story",
            modifier = modifier.fillMaxSize(),
        )
    } else {
        val retryableFailure = state.failure?.takeIf { it.retryable }
        HikariErrorState(
            title = "Story unavailable",
            message = state.failure?.let {
                "Source detail refresh failed: ${it.code}"
            },
            actionLabel = retryableFailure?.let { "Retry" },
            onAction = retryableFailure?.let { onRetry },
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StoryHeader(story: StoryUiModel) {
    Column(
        modifier = Modifier.padding(
            horizontal = MaterialTheme.hikariSpacing.large,
            vertical = MaterialTheme.hikariSpacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.extraSmall),
    ) {
        Text(story.preferredTitle, style = MaterialTheme.typography.headlineSmall)
        Text(story.contentType.displayName(), style = MaterialTheme.typography.bodyMedium)
        if (story.aliases.isNotEmpty()) {
            Text("Aliases: ${story.aliases.sorted().joinToString()}")
        }
    }
}

@Composable
private fun StoryFailure(failure: StoryRefreshFailure, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
    ) {
        Text(
            text = "Source detail refresh failed: ${failure.code}",
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry, enabled = failure.retryable) { Text("Retry") }
    }
}

@Composable
private fun StorySourceCard(source: CatalogEntry, selected: Boolean, onSelected: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = source.accessibilityDescription()
            }
            .padding(
                horizontal = MaterialTheme.hikariSpacing.large,
                vertical = MaterialTheme.hikariSpacing.small,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.extraSmall),
    ) {
        FilterChip(
            selected = selected,
            onClick = onSelected,
            label = { Text(source.pluginId.value) },
        )
        Text(source.title, style = MaterialTheme.typography.titleMedium)
        source.description?.let { Text(it) }
        if (source.authors.isNotEmpty()) Text("Authors: ${source.authors.sorted().joinToString()}")
        if (source.genres.isNotEmpty()) Text("Genres: ${source.genres.sorted().joinToString()}")
        source.sourceUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(source.scoreLabel(), style = MaterialTheme.typography.bodySmall)
    }
}

private fun StorySourceIdentity.matches(entry: CatalogEntry): Boolean =
    pluginId == entry.pluginId && sourceId == entry.sourceId

private fun CatalogEntry.scoreLabel(): String = score?.let { value ->
    "Score ${formatScore(value.value)} / ${formatScore(value.scale)}"
} ?: "Score unavailable"

private fun CatalogEntry.accessibilityDescription(): String =
    "$title, source ${pluginId.value}, ${scoreLabel()}"

private fun ContentType.displayName(): String = when (this) {
    ContentType.LIGHT_NOVEL -> "Light novel"
    ContentType.WEB_NOVEL -> "Web novel"
    ContentType.MANGA -> "Manga"
    ContentType.ANIME -> "Anime"
}

private fun formatScore(value: Double): String {
    val whole = value.toLong()
    return if (value == whole.toDouble()) whole.toString() else value.toString()
}
