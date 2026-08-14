package app.openstory.catalog.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.catalog.ui.activity.LibraryActivityItem
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariDimensions

@Composable
fun UpdatesScreen(
    state: UpdatesUiState,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        HikariDestinationScaffold(modifier) {
            Column(Modifier.fillMaxSize().padding(contentPadding)) {
                Text(
                    "Updates",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.hikariSpacing.space20,
                            vertical = MaterialTheme.hikariSpacing.space12,
                        )
                        .semantics { heading() },
                )
                when {
                    state.loading -> HikariLoadingState("Loading updates")
                    state.isEmpty -> HikariEmptyState(
                        "No reading updates",
                        message = "New mapped releases for stories in your Library will appear here.",
                    )
                    else -> UpdateGroups(state.groups, onStorySelected, onRead)
                }
            }
        }
    }
}

@Composable
private fun UpdateGroups(
    groups: List<UpdatesGroupUiModel>,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        groups.forEach { group ->
            item(key = "heading-${group.label}") {
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = MaterialTheme.hikariSpacing.space8).semantics { heading() },
                )
            }
            items(group.items, key = { it.releaseId.value }) { item ->
                UpdateCard(item, onStorySelected, onRead)
            }
        }
    }
}

@Composable
private fun UpdateCard(
    item: LibraryActivityItem,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.hikariDimensions.updateRowMinHeight)
            .semantics(mergeDescendants = true) {
            contentDescription = "${item.title}, ${item.chapterLabel}, ${item.sourceLabel}, ${item.languageTag}"
        },
        onClick = { onStorySelected(item.storyId) },
    ) {
        Row(
            Modifier.padding(MaterialTheme.hikariSpacing.space12),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val artwork = rememberHikariArtwork(
                HikariArtworkModel(item.coverUrl, item.storyId.value, item.title),
            )
            HikariArtwork(
                state = artwork,
                contentDescription = "${item.title} cover",
                modifier = Modifier
                    .width(MaterialTheme.hikariDimensions.posterUpdate.width)
                    .height(MaterialTheme.hikariDimensions.posterUpdate.height),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space6),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(item.chapterLabel, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${item.sourceLabel}  •  ${item.languageTag}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = { onRead(item.readerTarget) },
                modifier = Modifier.heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
            ) { Text("Read") }
        }
    }
}
