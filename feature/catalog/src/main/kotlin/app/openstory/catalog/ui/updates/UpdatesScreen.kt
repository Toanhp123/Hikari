package app.openstory.catalog.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariStickyDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.withScreenContentInsets
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun UpdatesScreen(
    state: UpdatesUiState,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onRetryContent: () -> Unit,
    onRetryObservation: () -> Unit,
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
            when (val content = state.content) {
                ContentState.Pending -> HikariLoadingState(
                    "Loading updates",
                    Modifier.padding(bodyPadding),
                )
                is ContentState.Failed -> HikariErrorState(
                    title = "Updates unavailable",
                    message = catalogFailureMessage(
                        content.failure.code,
                        "Couldn't load updates.",
                    ),
                    actionLabel = if (content.failure.retryable) "Retry" else null,
                    onAction = if (content.failure.retryable) onRetryContent else null,
                    modifier = Modifier.padding(bodyPadding),
                )
                is ContentState.Ready -> {
                    if (content.value.isEmpty) {
                        UpdatesEmptyContent(
                            observationIssue = state.observationIssue,
                            onRetryObservation = onRetryObservation,
                            bodyPadding = bodyPadding,
                        )
                    } else {
                        UpdateGroups(
                            groups = content.value.groups,
                            observationIssue = state.observationIssue,
                            onRetryObservation = onRetryObservation,
                            onStorySelected = onStorySelected,
                            onRead = onRead,
                            contentPadding = bodyPadding,
                            listState = listState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesEmptyContent(
    observationIssue: CatalogUiFailure?,
    onRetryObservation: () -> Unit,
    bodyPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .padding(bodyPadding)
            .fillMaxSize(),
    ) {
        UpdatesObservationFeedback(observationIssue, onRetryObservation)
        HikariEmptyState(
            "No reading updates",
            modifier = Modifier.weight(1f),
            message = "New mapped releases for stories in your Library will appear here.",
        )
    }
}

@Composable
private fun UpdatesObservationFeedback(
    observationIssue: CatalogUiFailure?,
    onRetryObservation: () -> Unit,
) {
    observationIssue?.let { issue ->
        HikariInlineFeedback(
            message = catalogFailureMessage(
                issue.code,
                "Couldn't update all update details.",
            ),
            actionLabel = if (issue.retryable) "Retry" else null,
            onAction = if (issue.retryable) onRetryObservation else null,
        )
    }
}

@Composable
private fun UpdateGroups(
    groups: List<UpdatesGroupUiModel>,
    observationIssue: CatalogUiFailure?,
    onRetryObservation: () -> Unit,
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
        if (observationIssue != null) {
            item(key = "updates-observation-feedback") {
                UpdatesObservationFeedback(observationIssue, onRetryObservation)
            }
        }
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
