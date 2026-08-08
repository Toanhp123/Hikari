package app.openstory.story.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun StoryDetailScreen(
    state: StoryDetailScreenState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val story = state.story
    if (story == null) {
        EmptyStoryDetail(state = state, onRetry = onRetry, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "story-header") {
            StoryHeader(story)
        }
        if (state.loading) {
            item(key = "story-loading") {
                Text("Refreshing source details…", modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        state.error?.let { error ->
            item(key = "story-error") {
                DetailError(error.code, onRetry)
            }
        }
        items(
            items = story.sources,
            key = { source -> "${source.pluginId.value}:${source.sourceId}" },
        ) { source ->
            StorySourceCard(source)
        }
    }
}

@Composable
private fun EmptyStoryDetail(
    state: StoryDetailScreenState,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (state.loading) "Loading story…" else "Story unavailable")
        state.error?.let { error ->
            DetailError(error.code, onRetry)
        }
    }
}

@Composable
private fun StoryHeader(story: StoryDetailStory) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(story.preferredTitle, style = MaterialTheme.typography.headlineSmall)
        Text(story.contentType.name, style = MaterialTheme.typography.bodyMedium)
        if (story.aliases.isNotEmpty()) {
            Text("Aliases: ${story.aliases.sorted().joinToString()}")
        }
    }
}

@Composable
private fun DetailError(
    code: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Source detail refresh failed: $code")
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun StorySourceCard(source: StoryDetailSource) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = sourceSemantics(source) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(source.pluginId.value, style = MaterialTheme.typography.titleSmall)
        Text(source.title, style = MaterialTheme.typography.titleMedium)
        source.description?.let { description -> Text(description) }
        if (source.authors.isNotEmpty()) {
            Text("Authors: ${source.authors.sorted().joinToString()}")
        }
        if (source.genres.isNotEmpty()) {
            Text("Genres: ${source.genres.sorted().joinToString()}")
        }
        source.sourceUrl?.let { url -> Text(url, style = MaterialTheme.typography.bodySmall) }
        Text(sourceScoreLabel(source), style = MaterialTheme.typography.bodySmall)
        Text("Fetched ${source.fetchedAtEpochMillis}", style = MaterialTheme.typography.bodySmall)
    }
}

private fun sourceScoreLabel(source: StoryDetailSource): String {
    val score = source.score
    val scale = source.scoreScale
    return if (score != null && scale != null) {
        "Score $score / $scale"
    } else {
        "Score unavailable"
    }
}

private fun sourceSemantics(source: StoryDetailSource): String = buildString {
    append(source.title)
    append(", source ")
    append(source.pluginId.value)
    append(", ")
    append(sourceScoreLabel(source))
    append(", fetched ")
    append(source.fetchedAtEpochMillis)
}
