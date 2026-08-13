package app.openstory.catalog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork

@Composable
fun StoryCoverCard(
    entry: CatalogEntry,
    sectionTitle: String,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onSelected(entry.storyId) },
        modifier = modifier.width(168.dp).semantics(mergeDescendants = true) {
            contentDescription = entry.accessibilityDescription(sectionTitle)
            traversalIndex = STORY_CARD_TRAVERSAL_INDEX
        },
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val artwork = rememberHikariArtwork(
                HikariArtworkModel(entry.coverUrl, entry.storyId.value, entry.title),
            )
            HikariArtwork(
                state = artwork,
                contentDescription = "${entry.title} cover",
                modifier = Modifier.fillMaxWidth().height(210.dp),
            )
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(entry.contentType.displayName(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun CatalogEntry.accessibilityDescription(sectionTitle: String): String {
    val scoreText = score?.let {
        " Score ${it.value.toAccessibleNumber()} out of ${it.scale.toAccessibleNumber()} from ${pluginId.value}."
    }
        ?: " Score unavailable from ${pluginId.value}."
    return "$title. ${contentType.displayName()}. Section $sectionTitle.$scoreText"
}

private fun ContentType.displayName(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun Double.toAccessibleNumber(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private const val STORY_CARD_TRAVERSAL_INDEX = 4f
