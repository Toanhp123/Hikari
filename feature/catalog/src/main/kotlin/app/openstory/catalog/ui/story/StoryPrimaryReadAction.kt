package app.openstory.catalog.ui.story

import app.openstory.catalog.ui.chapters.ChapterListContent
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.state.ContentState

internal sealed interface StoryPrimaryReadAction {
    data object CheckingChapters : StoryPrimaryReadAction
    data object ChaptersUnavailable : StoryPrimaryReadAction
    data object NoChapters : StoryPrimaryReadAction
    data object NoReleases : StoryPrimaryReadAction
    data object CheckingSources : StoryPrimaryReadAction
    data object FindSource : StoryPrimaryReadAction
    data class Read(val target: ReaderTarget, val isResume: Boolean) : StoryPrimaryReadAction
}

internal fun storyPrimaryReadAction(
    chapterState: ChapterListUiState?,
    resumeTarget: ReaderTarget?,
): StoryPrimaryReadAction = when (val contentState = chapterState?.content) {
    null,
    ContentState.Pending -> StoryPrimaryReadAction.CheckingChapters
    is ContentState.Failed -> StoryPrimaryReadAction.ChaptersUnavailable
    is ContentState.Ready -> contentState.value.toPrimaryReadAction(resumeTarget)
}

private fun ChapterListContent.toPrimaryReadAction(
    resumeTarget: ReaderTarget?,
): StoryPrimaryReadAction = when {
    chapterCount == 0 -> StoryPrimaryReadAction.NoChapters
    releaseTargets.isEmpty() -> StoryPrimaryReadAction.NoReleases
    !readerAvailabilityResolved -> StoryPrimaryReadAction.CheckingSources
    else -> {
        resumeTarget
            ?.takeIf(releaseTargets::contains)
            ?.let { target -> StoryPrimaryReadAction.Read(target, isResume = true) }
            ?: readableTargets.firstOrNull()
                ?.let { target -> StoryPrimaryReadAction.Read(target, isResume = false) }
            ?: StoryPrimaryReadAction.FindSource
    }
}
