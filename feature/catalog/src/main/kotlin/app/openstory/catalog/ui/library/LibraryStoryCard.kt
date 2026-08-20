package app.openstory.catalog.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.HikariListArtworkFrame
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.catalog.ui.components.StoryPosterCard
import kotlin.math.roundToInt
import app.openstory.designsystem.theme.hikariDimensions

@Composable
internal fun LibraryStoryCard(
    item: LibraryItemUiModel,
    displayMode: LibraryDisplayMode,
    onSelected: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val progress = item.progressFraction?.coerceIn(0f, 1f)
    if (displayMode == LibraryDisplayMode.GRID) {
        StoryPosterCard(
            storyId = item.storyId,
            title = item.title,
            coverUrl = item.coverUrl,
            contentDescription = item.accessibilityDescription(),
            onSelected = onSelected,
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .testTag("library-story-${item.storyId.value}"),
        ) {
            LibrarySupportingContent(item, progress)
        }
        return
    }

    HikariContentCard(
        onClick = onSelected,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .testTag("library-story-${item.storyId.value}")
            .semantics(mergeDescendants = true) { contentDescription = item.accessibilityDescription() },
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space12),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                item,
                Modifier
                    .width(MaterialTheme.hikariDimensions.posterList.width)
                    .height(MaterialTheme.hikariDimensions.posterList.height),
            )
            StoryMetadata(item, progress, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Artwork(item: LibraryItemUiModel, modifier: Modifier) {
    val artwork = rememberHikariArtwork(
        HikariArtworkModel(item.coverUrl, item.storyId.value, item.title),
    )
    HikariListArtworkFrame(modifier) {
        HikariArtwork(artwork, null, Modifier.matchParentSize())
    }
}

@Composable
private fun StoryMetadata(item: LibraryItemUiModel, progress: Float?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4)) {
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        LibrarySupportingContent(item, progress)
    }
}

@Composable
private fun LibrarySupportingContent(item: LibraryItemUiModel, progress: Float?) {
    Text(
        item.status.label(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    if (progress != null) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(progress.label(), style = MaterialTheme.typography.labelMedium)
    }
    Text(item.sourceState.label(), style = MaterialTheme.typography.bodySmall)
}

private fun LibraryItemUiModel.accessibilityDescription(): String = buildString {
    append(title)
    append(". ")
    append(status.label())
    append(". ")
    progressFraction?.let { append(it.coerceIn(0f, 1f).label()); append(". ") }
    append(sourceState.label())
    append('.')
}

private fun Float.label(): String = "${(this * PERCENT_MULTIPLIER).roundToInt()}% read"

private const val PERCENT_MULTIPLIER = 100
