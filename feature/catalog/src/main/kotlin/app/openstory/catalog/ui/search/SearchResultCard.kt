package app.openstory.catalog.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.search.CatalogSearchSourceCard
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.content.HikariCoverCardFrame
import app.openstory.designsystem.content.HikariMetadataBadge

@Composable
fun SearchResultCard(
    result: CatalogSearchStory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = result.sources.firstOrNull()
    val title = primary?.title ?: "Untitled story"
    val artwork = rememberHikariArtwork(
        HikariArtworkModel(primary?.coverUrl, result.story.id.value, title),
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics(mergeDescendants = true) { contentDescription = result.accessibilityDescription() },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HikariCoverCardFrame(Modifier.width(68.dp)) {
                HikariArtwork(artwork, "$title cover", Modifier.matchParentSize())
            }
            SearchResultMetadata(result, primary, title, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchResultMetadata(
    result: CatalogSearchStory,
    primary: CatalogSearchSourceCard?,
    title: String,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HikariMetadataBadge(result.story.contentType.displayName())
            primary?.score?.let {
                HikariMetadataBadge("${formatScore(it.value)}/${formatScore(it.scale)}")
            }
        }
        primary?.authors?.takeIf(Set<String>::isNotEmpty)?.let { authors ->
            Text(
                authors.sorted().joinToString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            result.sources.take(MAX_VISIBLE_SOURCES).forEachIndexed { index, source ->
                if (index > 0) Spacer(Modifier.size(6.dp))
                Text(
                    source.displayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun CatalogSearchStory.accessibilityDescription(): String = buildString {
    append(sources.firstOrNull()?.title ?: "Untitled story")
    append(". ")
    append(story.contentType.displayName())
    append(".")
    sources.forEach { source -> append(" ${source.displayLabel()}.") }
}

private fun CatalogSearchSourceCard.displayLabel(): String = buildString {
    append(pluginId.value)
    score?.let { append(" score ${formatScore(it.value)} out of ${formatScore(it.scale)}") }
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

private const val MAX_VISIBLE_SOURCES = 3
