package app.openstory.catalog.ui.discover

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.catalog.ui.components.catalogDisplayName
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkBackdrop
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.control.HikariPrimaryAction
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariBreakpoints
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariColors
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariTypography
import app.openstory.designsystem.theme.hikariHeroHorizontalScrim
import app.openstory.designsystem.theme.hikariHeroVerticalScrim

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
                "Featured ${entry.title}. Score $score from ${entry.pluginId.catalogDisplayName()}."
            traversalIndex = FEATURED_TRAVERSAL_INDEX
        },
    ) {
        val medium = maxWidth >= MaterialTheme.hikariBreakpoints.expandedContent
        val heroHeight = if (medium) {
            MaterialTheme.hikariDimensions.discoverHeroExpandedHeight
        } else {
            MaterialTheme.hikariDimensions.discoverHeroCompactHeight
        }
        val coverWidth = if (medium) {
            MaterialTheme.hikariDimensions.discoverHeroExpandedPosterWidth
        } else {
            MaterialTheme.hikariDimensions.discoverHeroCompactPosterWidth
        }
        val shape = MaterialTheme.hikariShapes.hero
        val heroClickModifier = if (medium) {
            Modifier
        } else {
            Modifier.clickable { onSelected(entry.storyId) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clip(shape)
                .then(heroClickModifier),
        ) {
            HikariArtworkBackdrop(
                state = artwork,
                modifier = Modifier.fillMaxSize(),
                scrim = MaterialTheme.hikariHeroHorizontalScrim,
                overlayScrim = MaterialTheme.hikariHeroVerticalScrim,
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(MaterialTheme.hikariSpacing.space8),
                horizontalArrangement = Arrangement.spacedBy(
                    if (medium) MaterialTheme.hikariSpacing.space20 else MaterialTheme.hikariSpacing.itemGap,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HikariArtwork(
                    state = artwork,
                    contentDescription = "${entry.title} featured cover",
                    modifier = Modifier
                        .width(coverWidth)
                        .fillMaxHeight()
                        .clip(MaterialTheme.hikariShapes.contentCard),
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
        verticalArrangement = Arrangement.spacedBy(
            if (medium) MaterialTheme.hikariSpacing.space8 else MaterialTheme.hikariSpacing.space4,
        ),
    ) {
        Text(
            "FEATURED STORY",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.hikariTypography.heroEyebrow,
        )
        Text(
            entry.title,
            color = MaterialTheme.hikariColors.onArtwork,
            style = if (medium) {
                MaterialTheme.hikariTypography.heroTitleExpanded
            } else {
                MaterialTheme.hikariTypography.heroTitleCompact
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.score?.let { score ->
            Text(
                "${score.value.toAccessibleNumber()} / ${score.scale.toAccessibleNumber()}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            entry.heroMetadata(),
            color = MaterialTheme.hikariColors.onArtwork.copy(alpha = MaterialTheme.hikariOpacity.onArtworkSecondary),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (medium) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
            HikariMetadataBadge(
                entry.pluginId.catalogDisplayName(),
                containerColor = MaterialTheme.hikariColors.onArtwork.copy(
                    alpha = MaterialTheme.hikariOpacity.onArtworkBadge,
                ),
                contentColor = MaterialTheme.hikariColors.onArtwork,
            )
            entry.languageTags.firstOrNull()?.takeIf { medium }?.let { language ->
                HikariMetadataBadge(
                    language.uppercase(),
                    containerColor = MaterialTheme.hikariColors.onArtwork.copy(
                    alpha = MaterialTheme.hikariOpacity.onArtworkBadge,
                ),
                    contentColor = MaterialTheme.hikariColors.onArtwork,
                )
            }
        }
        if (medium) {
            HikariPrimaryAction(onClick = { onSelected(entry.storyId) }) {
                Text("Open story", style = MaterialTheme.hikariTypography.heroAction)
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
