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
import app.openstory.designsystem.layout.HikariStickyDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.plus
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
        contentPadding = contentPadding.plus(
            start = MaterialTheme.hikariSpacing.space16,
            end = MaterialTheme.hikariSpacing.space16,
            bottom = MaterialTheme.hikariSpacing.space16,
        ),
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
                    action = item.readerTarget?.let { target ->
                        StoryUpdateCardAction("Read") { onRead(target) }
                    },
                    variant = StoryUpdateCardVariant.ROW,
                )
            }
        }
    }
}
