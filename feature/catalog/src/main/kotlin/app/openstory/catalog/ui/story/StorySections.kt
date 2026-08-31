package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.openstory.catalog.ui.chapters.ChapterList
import app.openstory.catalog.ui.chapters.ChapterListActions
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.common.id.PluginId
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.theme.hikariDimensions

@Composable
internal fun StorySectionTabs(selectedSection: StorySection, onSelected: (StorySection) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selectedSection.ordinal) {
        StorySection.entries.forEach { section ->
            val selected = section == selectedSection
            Tab(
                selected = selected,
                onClick = { onSelected(section) },
                modifier = Modifier
                    .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                    .testTag("story-tab-${section.name.lowercase()}")
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                        stateDescription = if (selected) "Active section" else "Inactive section"
                    },
                text = { Text(section.label()) },
            )
        }
    }
}

@Composable
internal fun StorySectionContent(
    state: StoryUiState,
    story: StoryUiModel,
    onRefresh: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    onSourceRefresh: (PluginId, String) -> Unit,
    onPinPrimary: (PluginId, String) -> Unit,
    onUseAutomaticPrimary: () -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    chapterState: ChapterListUiState?,
    chapterActions: ChapterListActions,
    modifier: Modifier,
) {
    when (state.selectedSection) {
        StorySection.OVERVIEW -> StoryOverview(
            story = story,
            modifier = modifier,
            refreshing = state.refresh.inProgress,
            onRefresh = onRefresh,
        )
        StorySection.CHAPTERS -> ChapterList(
            state = chapterState ?: ChapterListUiState(state.storyId),
            actions = chapterActions,
            modifier = modifier,
            contentPadding = storySectionContentPadding(),
        )
        StorySection.SOURCES -> StorySources(
            story = story,
            selectedSource = state.selectedSource,
            refreshing = state.refresh.inProgress,
            onRefresh = onRefresh,
            onSourceSelected = onSourceSelected,
            onSourceRefresh = onSourceRefresh,
            onPinPrimary = onPinPrimary,
            onUseAutomaticPrimary = onUseAutomaticPrimary,
            mappingState = mappingState,
            mappingActions = mappingActions,
            modifier = modifier,
        )
    }
}

@Composable
internal fun StoryRefreshFailureBanner(
    failure: CatalogUiFailure,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    HikariInlineFeedback(
        message = catalogFailureMessage(failure.code, "Couldn't refresh story details."),
        actionLabel = if (failure.retryable) "Retry" else null,
        actionEnabled = !refreshing,
        onAction = if (failure.retryable) onRefresh else null,
        actionModifier = Modifier.testTag("story-retry"),
    )
}

@Composable
internal fun StoryObservationIssueBanner(
    failure: CatalogUiFailure,
    onRetryObservation: () -> Unit,
) {
    HikariInlineFeedback(
        message = catalogFailureMessage(failure.code, "Some story details may be out of date."),
        actionLabel = if (failure.retryable) "Retry" else null,
        onAction = if (failure.retryable) onRetryObservation else null,
        actionModifier = Modifier.testTag("story-observation-retry"),
    )
}

@Composable
internal fun StoryCommandFailureBanner(failure: CatalogUiFailure) {
    HikariInlineFeedback(
        message = catalogFailureMessage(failure.code, "Couldn't update the story source preference."),
    )
}

internal fun StorySection.showsSourceDetailFailure(): Boolean = this != StorySection.CHAPTERS

private fun StorySection.label() = when (this) {
    StorySection.OVERVIEW -> "Overview"
    StorySection.CHAPTERS -> "Chapters"
    StorySection.SOURCES -> "Sources"
}
