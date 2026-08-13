package app.openstory.catalog.ui.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.unit.dp
import app.openstory.catalog.ui.activity.LibraryActivityItem
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.StoryId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState

@Composable
fun UpdatesScreen(
    state: UpdatesUiState,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Text(
            "Updates",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).semantics { heading() },
        )
        when {
            state.loading -> HikariLoadingState("Loading updates")
            state.isEmpty -> HikariEmptyState(
                "No reading updates",
                message = "New mapped releases for stories in your Library will appear here.",
            )
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "heading-${group.label}") {
                        Text(group.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                    }
                    items(group.items, key = { it.releaseId.value }) { item -> UpdateCard(item, onStorySelected, onRead) }
                }
            }
        }
    }
    }
}

@Composable
private fun UpdateCard(item: LibraryActivityItem, onStorySelected: (StoryId) -> Unit, onRead: (ReaderTarget) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp).semantics(mergeDescendants = true) {
            contentDescription = "${item.title}, ${item.chapterLabel}, ${item.sourceLabel}, ${item.languageTag}"
        },
        onClick = { onStorySelected(item.storyId) },
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val artwork = rememberHikariArtwork(HikariArtworkModel(item.coverUrl, item.storyId.value, item.title))
            HikariArtwork(artwork, "${item.title} cover", Modifier.width(58.dp).height(82.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.chapterLabel, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${item.sourceLabel}  •  ${item.languageTag}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { onRead(item.readerTarget) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Read") }
        }
    }
}
