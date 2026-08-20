package app.openstory.catalog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.content.HikariCoverCardFrame
import app.openstory.designsystem.content.HikariCoverCardVariant
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariTypography

@Composable
fun StoryCoverCard(
    entry: CatalogEntry,
    sectionTitle: String,
    onSelected: (StoryId) -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    StoryPosterCard(
        storyId = entry.storyId,
        title = entry.title,
        coverUrl = entry.coverUrl,
        contentDescription = entry.accessibilityDescription(sectionTitle),
        onSelected = { onSelected(entry.storyId) },
        metadata = entry.cardMetadata(),
        modifier = modifier.width(width),
    )
}

@Composable
fun StoryPosterCard(
    storyId: StoryId,
    title: String,
    coverUrl: String?,
    contentDescription: String,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    traversalIndex: Float = STORY_CARD_TRAVERSAL_INDEX,
    supportingContent: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .clickable(onClick = onSelected)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.traversalIndex = traversalIndex
            },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        val artwork = rememberHikariArtwork(
            HikariArtworkModel(coverUrl, storyId.value, title),
        )
        HikariCoverCardFrame(
            modifier = Modifier.fillMaxWidth().testTag("story-poster-card"),
            variant = HikariCoverCardVariant.POSTER,
        ) {
            HikariArtwork(
                state = artwork,
                contentDescription = "$title cover",
                modifier = Modifier.matchParentSize(),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.hikariTypography.posterTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        metadata?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        supportingContent()
    }
}

private fun CatalogEntry.cardMetadata(): String = buildString {
    append(contentType.displayName())
    score?.let {
        append(" / ")
        append(it.value.toAccessibleNumber())
    }
}

private fun CatalogEntry.accessibilityDescription(sectionTitle: String): String {
    val scoreText = score?.let {
        " Score ${it.value.toAccessibleNumber()} out of ${it.scale.toAccessibleNumber()} from ${pluginId.value}."
    }
        ?: " Score unavailable from ${pluginId.value}."
    return "$title. ${contentType.displayName()}. Section $sectionTitle.$scoreText"
}

private fun ContentType.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun Double.toAccessibleNumber(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private const val STORY_CARD_TRAVERSAL_INDEX = 4f
