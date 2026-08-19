package app.openstory.catalog.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.openstory.catalog.ui.activity.LibraryActivityItem
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.components.StoryUpdateCard
import app.openstory.catalog.ui.components.StoryUpdateCardAction
import app.openstory.catalog.ui.components.StoryUpdateCardContent
import app.openstory.catalog.ui.components.StoryUpdateCardVariant
import app.openstory.common.id.StoryId
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariStickyDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.withScreenContentInsets
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun UpdatesScreen(
    state: UpdatesUiState,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val listState = rememberLazyListState()
    val headerScrolled = remember {
        derivedStateOf { listState.canScrollBackward }
    }
    HikariDestinationScaffold(modifier) {
        HikariStickyDestinationScaffold(
            contentPadding = contentPadding,
            header = { HikariTopLevelHeader(title = "Updates") },
            headerScrolled = headerScrolled.value,
        ) { bodyPadding ->
            when {
                state.loading -> HikariLoadingState(
                    "Loading updates",
                    Modifier.padding(bodyPadding),
                )
                state.isEmpty -> HikariEmptyState(
                    "No reading updates",
                    modifier = Modifier.padding(bodyPadding),
                    message = "New mapped releases for stories in your Library will appear here.",
                )
                else -> UpdateGroups(state.groups, onStorySelected, onRead, bodyPadding, listState)
            }
        }
    }
}

@Composable
private fun UpdateGroups(
    groups: List<UpdatesGroupUiModel>,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding.withScreenContentInsets(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
    ) {
        groups.filter { it.items.isNotEmpty() }.forEachIndexed { groupIndex, group ->
            val firstItem = group.items.first()
            item(key = "heading-${group.label}") {
                HikariSectionLead(
                    separatedFromPreviousSection = groupIndex > 0,
                    header = { HikariSectionHeader(title = group.label) },
                    firstContent = { UpdateListItem(firstItem, onStorySelected, onRead) },
                )
            }
            items(group.items.drop(1), key = { it.releaseId.value }) { item ->
                UpdateListItem(item, onStorySelected, onRead)
            }
        }
    }
}

@Composable
private fun UpdateListItem(
    item: LibraryActivityItem,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
) {
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
        action = item.readerTarget?.let { target ->
            StoryUpdateCardAction("Read") { onRead(target) }
        },
        variant = StoryUpdateCardVariant.ROW,
    )
}
