package app.openstory.catalog.ui.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.catalog.ui.chapters.ChapterList
import app.openstory.catalog.ui.chapters.ChapterListActions
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingSheet
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.designsystem.layout.HikariResponsiveContent
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.library.LibraryStatus

@Composable
fun StoryScreen(
    state: StoryUiState,
    onRetry: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    onSectionSelected: (StorySection) -> Unit = {},
    onLibraryStatusSelected: (LibraryStatus?) -> Unit = {},
    onRead: (ReaderTarget) -> Unit = {},
    onDownload: (ChapterReleaseId) -> Unit = {},
    mappingState: MappingUiState? = null,
    mappingActions: MappingActions = MappingActions(),
    chapterState: ChapterListUiState? = null,
    chapterActions: ChapterListActions = ChapterListActions(),
    modifier: Modifier = Modifier,
) {
    val story = state.story
    if (story == null) {
        EmptyStory(state, onRetry, modifier)
        return
    }
    val readableTargets = chapterState?.readableTargets.orEmpty()
    val validatedResumeTarget = state.resumeTarget?.takeIf { target ->
        readableTargets.any { it.chapterId == target.chapterId && it.releaseId == target.releaseId }
    }
    val firstReadableTarget = readableTargets.firstOrNull()
    val readerTarget = validatedResumeTarget ?: firstReadableTarget
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        HikariResponsiveContent(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (windowClass == HikariWindowClass.MEDIUM) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(0.44f).fillMaxSize().testTag("story-summary-pane").semantics {
                        isTraversalGroup = true
                        traversalIndex = 0f
                    },
                ) {
                    StoryHero(
                        story, state.libraryStatus, readerTarget,
                        validatedResumeTarget != null,
                        firstReadableTarget?.releaseId,
                        onLibraryStatusSelected, onRead, onDownload,
                        narrow = true,
                    )
                    StoryOverview(story, compact = true, modifier = Modifier.weight(1f))
                }
                Column(
                    Modifier.weight(0.56f).fillMaxSize().testTag("story-content-pane").semantics {
                        isTraversalGroup = true
                        traversalIndex = 1f
                    },
                ) {
                    StorySectionTabs(state.selectedSection, onSectionSelected)
                    if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.failure?.let { StoryFailureBanner(it, state.refreshing, onRetry) }
                    StorySectionContent(
                        state, onRetry, onSourceSelected, mappingState, mappingActions,
                        chapterState, chapterActions, Modifier.weight(1f),
                    )
                }
            }
            } else {
                Column(Modifier.fillMaxSize()) {
                StoryHero(
                    story, state.libraryStatus, readerTarget,
                    validatedResumeTarget != null,
                    firstReadableTarget?.releaseId,
                    onLibraryStatusSelected, onRead, onDownload,
                )
                if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                StorySectionTabs(state.selectedSection, onSectionSelected)
                state.failure?.let { StoryFailureBanner(it, state.refreshing, onRetry) }
                StorySectionContent(
                    state, onRetry, onSourceSelected, mappingState, mappingActions,
                    chapterState, chapterActions, Modifier.weight(1f),
                )
                }
            }
        }
    }
}

@Composable
private fun StorySectionTabs(selectedSection: StorySection, onSelected: (StorySection) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selectedSection.ordinal) {
        StorySection.entries.forEach { section ->
            val selected = section == selectedSection
            Tab(
                selected = selected,
                onClick = { onSelected(section) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
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
private fun StorySectionContent(
    state: StoryUiState,
    onRetry: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    chapterState: ChapterListUiState?,
    chapterActions: ChapterListActions,
    modifier: Modifier,
) {
    when (state.selectedSection) {
        StorySection.OVERVIEW -> StoryOverview(requireNotNull(state.story), modifier = modifier)
        StorySection.CHAPTERS -> ChapterList(chapterState ?: ChapterListUiState(state.storyId), chapterActions, modifier)
        StorySection.SOURCES -> StorySources(
            story = requireNotNull(state.story),
            selectedSource = state.selectedSource,
            refreshing = state.refreshing,
            failure = null,
            onRetry = onRetry,
            onSourceSelected = onSourceSelected,
            mappingState = mappingState,
            mappingActions = mappingActions,
            modifier = modifier,
        )
    }
}

@Composable
private fun StoryFailureBanner(failure: StoryRefreshFailure, refreshing: Boolean, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            "Source detail refresh failed: ${failure.code}",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        if (failure.retryable) {
            TextButton(
                onClick = onRetry,
                enabled = !refreshing,
                modifier = Modifier.heightIn(min = 48.dp).testTag("story-retry"),
            ) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyStory(state: StoryUiState, onRetry: () -> Unit, modifier: Modifier) {
    if (state.refreshing) {
        HikariLoadingState("Loading story", modifier.fillMaxSize())
    } else {
        val retryable = state.failure?.retryable == true
        HikariErrorState(
            title = "Story unavailable",
            message = state.failure?.let { "Source detail refresh failed: ${it.code}" },
            actionLabel = if (retryable) "Retry" else null,
            onAction = if (retryable) onRetry else null,
            modifier = modifier.fillMaxSize(),
        )
    }
}

private fun StorySection.label() = when (this) {
    StorySection.OVERVIEW -> "Overview"
    StorySection.CHAPTERS -> "Chapters"
    StorySection.SOURCES -> "Sources"
}
