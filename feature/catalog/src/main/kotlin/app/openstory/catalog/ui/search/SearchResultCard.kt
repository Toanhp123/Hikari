package app.openstory.catalog.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.search.CatalogSearchSourceCard
import app.openstory.catalog.search.CatalogSearchStory

@Composable
fun SearchResultCard(
    result: CatalogSearchStory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = result.sources.firstOrNull()
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = result.accessibilityDescription()
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(primary?.title ?: "Untitled story", style = MaterialTheme.typography.titleMedium)
            Text(result.story.contentType.displayName(), style = MaterialTheme.typography.labelMedium)
            result.sources.forEach { source ->
                Text(source.displayLabel(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun CatalogSearchStory.accessibilityDescription(): String = buildString {
    append(sources.firstOrNull()?.title ?: "Untitled story")
    append(". ")
    append(story.contentType.displayName())
    append(".")
    sources.forEach { source ->
        append(" ")
        append(source.displayLabel())
        append(".")
    }
}

private fun CatalogSearchSourceCard.displayLabel(): String = buildString {
    append(pluginId.value)
    score?.let { value ->
        append(" score ")
        append(formatScore(value.value))
        append(" out of ")
        append(formatScore(value.scale))
    }
}

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
