package app.openstory.catalog.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.catalog.model.PublicationStatus
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.HikariListArtworkFrame
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun DiscoverTopRatedRow(
    rank: Int,
    item: DiscoverStoryItem,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val genreText = item.genres.take(3).joinToString(" · ")
    val statusText = item.publicationStatus?.displayName()
    val ratingText = item.score?.let { score ->
        "${score.value.toAccessibleNumber()} out of ${score.scale.toAccessibleNumber()}"
    }
    val description = buildList {
        add("Rank $rank")
        add(item.title)
        ratingText?.let { add("rating $it") }
        if (genreText.isNotBlank()) add(genreText)
        statusText?.let { add(it) }
    }.joinToString(", ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.hikariDimensions.updateRowMinHeight)
            .clickable(role = Role.Button) { onSelected(item.storyId) }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("discover-top-rated-rank-$rank"),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString().padStart(2, '0'),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        val artworkSize = MaterialTheme.hikariDimensions.posterUpdate
        HikariListArtworkFrame(
            Modifier
                .width(artworkSize.width)
                .height(artworkSize.height),
        ) {
            HikariArtwork(
                state = rememberHikariArtwork(
                    HikariArtworkModel(item.coverUrl, item.storyId.value, item.title),
                ),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.score?.let { score ->
                Text(
                    text = "★ ${score.value.toAccessibleNumber()} / ${score.scale.toAccessibleNumber()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            if (genreText.isNotBlank()) {
                Text(
                    text = genreText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            statusText?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun PublicationStatus.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
