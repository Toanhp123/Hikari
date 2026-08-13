package app.openstory.catalog.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkBackdrop
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork

@Composable
fun DiscoverHero(entry: CatalogEntry, onSelected: (StoryId) -> Unit, modifier: Modifier = Modifier) {
    val artwork = rememberHikariArtwork(HikariArtworkModel(entry.coverUrl, entry.storyId.value, entry.title))
    Box(
        modifier = modifier.fillMaxWidth().height(280.dp).clickable { onSelected(entry.storyId) }
            .semantics {
                val score = entry.score?.let {
                    "${it.value.toAccessibleNumber()} out of ${it.scale.toAccessibleNumber()}"
                } ?: "unavailable"
                contentDescription = "Featured ${entry.title}. Score $score from ${entry.pluginId.value}."
                traversalIndex = FEATURED_TRAVERSAL_INDEX
            },
    ) {
        HikariArtworkBackdrop(artwork, Modifier.matchParentSize())
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            HikariArtwork(
                artwork,
                "${entry.title} featured cover",
                Modifier.height(168.dp).fillMaxWidth(FEATURED_COVER_WIDTH_FRACTION),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
                Text(entry.pluginId.value, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

private fun Double.toAccessibleNumber(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private const val FEATURED_TRAVERSAL_INDEX = 4f
private const val FEATURED_COVER_WIDTH_FRACTION = 0.34f
