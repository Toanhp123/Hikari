package app.openstory.catalog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId

interface HomeCoverRenderer {
    @Composable
    fun render(reference: String?, title: String, modifier: Modifier)
}

object PlaceholderHomeCoverRenderer : HomeCoverRenderer {
    @Composable
    override fun render(reference: String?, title: String, modifier: Modifier) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.trim().take(1).ifEmpty { "?" },
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
fun HomeCard(
    entry: CatalogEntry,
    sectionTitle: String,
    onStorySelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
    coverRenderer: HomeCoverRenderer = PlaceholderHomeCoverRenderer,
) {
    Card(
        onClick = { onStorySelected(entry.storyId) },
        modifier = modifier
            .width(CARD_WIDTH)
            .semantics(mergeDescendants = true) {
                contentDescription = entry.accessibilityDescription(sectionTitle)
            },
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            coverRenderer.render(
                reference = entry.coverUrl,
                title = entry.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(COVER_HEIGHT),
            )
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(entry.contentType.displayName(), style = MaterialTheme.typography.labelMedium)
            if (entry.authors.isNotEmpty()) {
                Text(
                    text = entry.authors.sorted().joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entry.score?.let { score ->
                Text(
                    text = "${formatScore(score.value)} / ${formatScore(score.scale)} - ${entry.pluginId.value}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun CatalogEntry.accessibilityDescription(sectionTitle: String): String {
    val scoreText = score?.let { value ->
        " Score ${formatScore(value.value)} out of ${formatScore(value.scale)} from ${pluginId.value}."
    } ?: " Score unavailable from ${pluginId.value}."
    return "$title. ${contentType.displayName()}. Section $sectionTitle.$scoreText"
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

private val CARD_WIDTH = 184.dp
private val COVER_HEIGHT = 220.dp
private val CARD_PADDING = 12.dp
private val CARD_GAP = 6.dp
