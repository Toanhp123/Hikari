package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.catalog.ui.chapters.ChapterListActions
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariResponsiveContent
import app.openstory.designsystem.layout.HikariSafeDestinationViewport
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.library.LibraryStatus

@Composable
fun StoryScreen(
    state: StoryUiState,
    onRefresh: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    onRetryContent: () -> Unit = {},
    onRetryObservation: () -> Unit = {},
    onPinPrimary: (PluginId, String) -> Unit = { _, _ -> },
    onUseAutomaticPrimary: () -> Unit = {},
    onSectionSelected: (StorySection) -> Unit = {},
    onLibraryStatusSelected: (LibraryStatus?) -> Unit = {},
    onReconciliationMerge: () -> Unit = {},
    onReconciliationKeepSeparate: () -> Unit = {},
    onReconciliationDefer: () -> Unit = {},
    onRead: (ReaderTarget) -> Unit = {},
    onDownload: (ChapterReleaseId) -> Unit = {},
    mappingState: MappingUiState? = null,
    mappingActions: MappingActions = MappingActions(),
    chapterState: ChapterListUiState? = null,
    chapterActions: ChapterListActions = ChapterListActions(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    when (val content = state.content) {
        is ContentState.Pending -> {
            StoryBlockingState(contentPadding, modifier) { bodyModifier ->
                HikariLoadingState("Loading story", bodyModifier)
            }
            return
        }
        is ContentState.Failed -> {
            StoryBlockingState(contentPadding, modifier) { bodyModifier ->
                val retryable = content.failure.retryable
                HikariErrorState(
                    title = "Story unavailable",
                    message = catalogFailureMessage(content.failure.code, "Couldn't load story details."),
                    actionLabel = if (retryable) "Retry" else null,
                    onAction = if (retryable) onRetryContent else null,
                    modifier = bodyModifier,
                )
            }
            return
        }
        is ContentState.Ready -> StoryReadyContent(
            state = state,
            story = content.value,
            onRefresh = onRefresh,
            onRetryObservation = onRetryObservation,
            onSourceSelected = onSourceSelected,
            onPinPrimary = onPinPrimary,
            onUseAutomaticPrimary = onUseAutomaticPrimary,
            onSectionSelected = onSectionSelected,
            onLibraryStatusSelected = onLibraryStatusSelected,
            onReconciliationMerge = onReconciliationMerge,
            onReconciliationKeepSeparate = onReconciliationKeepSeparate,
            onReconciliationDefer = onReconciliationDefer,
            onRead = onRead,
            onDownload = onDownload,
            mappingState = mappingState,
            mappingActions = mappingActions,
            chapterState = chapterState,
            chapterActions = chapterActions,
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun StoryBlockingState(
    contentPadding: PaddingValues,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    HikariDestinationScaffold(modifier) {
        HikariSafeDestinationViewport(contentPadding) { safeBodyPadding ->
            content(Modifier.fillMaxSize().padding(safeBodyPadding))
        }
    }
}

@Composable
private fun StoryReadyContent(
    state: StoryUiState,
    story: StoryUiModel,
    onRefresh: () -> Unit,
    onRetryObservation: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    onPinPrimary: (PluginId, String) -> Unit,
    onUseAutomaticPrimary: () -> Unit,
    onSectionSelected: (StorySection) -> Unit,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onReconciliationMerge: () -> Unit,
    onReconciliationKeepSeparate: () -> Unit,
    onReconciliationDefer: () -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    chapterState: ChapterListUiState?,
    chapterActions: ChapterListActions,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val primaryReadAction = storyPrimaryReadAction(chapterState, state.resumeTarget)
    val selectedReadTarget = (primaryReadAction as? StoryPrimaryReadAction.Read)?.target
    val downloadableReleaseId = selectedReadTarget
        ?.takeIf { target ->
            val chapterContent = (chapterState?.content as? ContentState.Ready)?.value
            chapterContent?.downloadableTargets?.contains(target) == true
        }
        ?.releaseId
    HikariDestinationScaffold(modifier) {
        HikariSafeDestinationViewport(contentPadding) { safeBodyPadding ->
            StoryReconciliationPromptHost(
                prompt = state.reconciliationPrompt,
                resolving = state.reconciliationResolving,
                failureMessage = state.reconciliationFailureMessage,
                onMerge = onReconciliationMerge,
                onKeepSeparate = onReconciliationKeepSeparate,
                onDefer = onReconciliationDefer,
                modifier = Modifier.fillMaxSize().padding(safeBodyPadding),
            ) {
                HikariResponsiveContent(Modifier.weight(1f)) {
                    if (windowClass == HikariWindowClass.MEDIUM) {
                        MediumStoryLayout(
                            state,
                            story,
                            primaryReadAction,
                            downloadableReleaseId,
                            onRefresh,
                            onRetryObservation,
                            onSourceSelected,
                            onPinPrimary,
                            onUseAutomaticPrimary,
                            onSectionSelected,
                            onLibraryStatusSelected,
                            onRead,
                            { onSectionSelected(StorySection.SOURCES) },
                            onDownload,
                            mappingState,
                            mappingActions,
                            chapterState,
                            chapterActions,
                        )
                    } else {
                        CompactStoryLayout(
                            state,
                            story,
                            primaryReadAction,
                            downloadableReleaseId,
                            onRefresh,
                            onRetryObservation,
                            onSourceSelected,
                            onPinPrimary,
                            onUseAutomaticPrimary,
                            onSectionSelected,
                            onLibraryStatusSelected,
                            onRead,
                            { onSectionSelected(StorySection.SOURCES) },
                            onDownload,
                            mappingState,
                            mappingActions,
                            chapterState,
                            chapterActions,
                            narrowHero = windowClass == HikariWindowClass.COMPACT,
                        )
                    }
                }
            }
        }
    }
}
