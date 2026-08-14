package app.openstory.catalog.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.components.StoryUpdateCard
import app.openstory.catalog.ui.components.StoryUpdateCardAction
import app.openstory.catalog.ui.components.StoryUpdateCardContent
import app.openstory.catalog.ui.components.StoryUpdateCardVariant
import app.openstory.common.id.StoryId
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun UpdatesScreen(
    state: UpdatesUiState,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    HikariDestinationScaffold(modifier) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            HikariTopLevelHeader(title = "Updates")
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
                HikariSectionHeader(
                    title = group.label,
                    modifier = Modifier.padding(top = MaterialTheme.hikariSpacing.space8),
                )
            }
            items(group.items, key = { it.releaseId.value }) { item ->
                StoryUpdateCard(
                    content = StoryUpdateCardContent(
                        storyId = item.storyId,
                        title = item.title,
                        coverUrl = item.coverUrl,
                        chapterLabel = item.chapterLabel,
                        sourceLabel = item.sourceLabel,
                        languageTag = item.languageTag,
                        contentDescription =
                            "${item.title}, ${item.chapterLabel}, ${item.sourceLabel}, ${item.languageTag}",
                    ),
                    onClick = { onStorySelected(item.storyId) },
                    action = StoryUpdateCardAction("Read") { onRead(item.readerTarget) },
                    variant = StoryUpdateCardVariant.ROW,
                )
            }
        }
    }
}
