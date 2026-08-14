package app.openstory.catalog.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.catalog.ui.components.StoryPosterCard
import kotlin.math.roundToInt

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

    Card(
        onClick = onSelected,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .testTag("library-story-${item.storyId.value}")
            .semantics(mergeDescendants = true) { contentDescription = item.accessibilityDescription() },
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(item, Modifier.width(72.dp).height(104.dp))
            StoryMetadata(item, progress, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Artwork(item: LibraryItemUiModel, modifier: Modifier) {
    val artwork = rememberHikariArtwork(
        HikariArtworkModel(item.coverUrl, item.storyId.value, item.title),
    )
    HikariArtwork(artwork, null, modifier)
}

@Composable
private fun StoryMetadata(item: LibraryItemUiModel, progress: Float?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
