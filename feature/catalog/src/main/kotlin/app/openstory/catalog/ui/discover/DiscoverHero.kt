package app.openstory.catalog.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkBackdrop
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.content.HikariMetadataBadge

@Composable
fun DiscoverHero(entry: CatalogEntry, onSelected: (StoryId) -> Unit, modifier: Modifier = Modifier) {
    val artwork = rememberHikariArtwork(
        HikariArtworkModel(entry.coverUrl, entry.storyId.value, entry.title),
    )
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().semantics {
            val score = entry.score?.let {
                "${it.value.toAccessibleNumber()} out of ${it.scale.toAccessibleNumber()}"
            } ?: "unavailable"
            contentDescription =
                "Featured ${entry.title}. Score $score from ${entry.pluginId.discoverDisplayName()}."
            traversalIndex = FEATURED_TRAVERSAL_INDEX
        },
    ) {
        val medium = maxWidth >= 520.dp
        val heroHeight = if (medium) 246.dp else 176.dp
        val coverWidth = if (medium) 156.dp else 96.dp
        val shape = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clip(shape)
                .clickable { onSelected(entry.storyId) },
        ) {
            HikariArtworkBackdrop(
                state = artwork,
                modifier = Modifier.fillMaxSize(),
                scrim = Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.18f),
                        Color.Black.copy(alpha = 0.68f),
                        Color.Black.copy(alpha = 0.92f),
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                    ),
                ),
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (medium) 20.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HikariArtwork(
                    state = artwork,
                    contentDescription = "${entry.title} featured cover",
                    modifier = Modifier
                        .width(coverWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp)),
                )
                HeroDetails(entry, medium, onSelected, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroDetails(
    entry: CatalogEntry,
    medium: Boolean,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (medium) 6.dp else 4.dp),
    ) {
        Text(
            "FEATURED STORY",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            entry.title,
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            style = if (medium) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.score?.let { score ->
            Text(
                "${score.value.toAccessibleNumber()} / ${score.scale.toAccessibleNumber()}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            entry.heroMetadata(),
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (medium) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HikariMetadataBadge(
                entry.pluginId.discoverDisplayName(),
                containerColor = Color.White.copy(alpha = 0.13f),
                contentColor = Color.White,
            )
            entry.languageTags.firstOrNull()?.takeIf { medium }?.let { language ->
                HikariMetadataBadge(
                    language.uppercase(),
                    containerColor = Color.White.copy(alpha = 0.13f),
                    contentColor = Color.White,
                )
            }
        }
        if (medium) {
            Surface(
                onClick = { onSelected(entry.storyId) },
                color = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    "Open story",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

private fun CatalogEntry.heroMetadata(): String = buildList {
    add(contentType.displayName())
    addAll(genres.sorted().take(2))
    if (authors.isNotEmpty()) add(authors.sorted().first())
}.joinToString("  /  ")

private fun ContentType.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun Double.toAccessibleNumber(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private const val FEATURED_TRAVERSAL_INDEX = 4f
