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
): StoryPrimaryReadAction {
    val contentState = chapterState?.content ?: return StoryPrimaryReadAction.CheckingChapters
    val content = when (contentState) {
        ContentState.Pending -> return StoryPrimaryReadAction.CheckingChapters
        is ContentState.Failed -> return StoryPrimaryReadAction.ChaptersUnavailable
        is ContentState.Ready -> contentState.value
    }
    return content.toPrimaryReadAction(resumeTarget)
}

private fun ChapterListContent.toPrimaryReadAction(resumeTarget: ReaderTarget?): StoryPrimaryReadAction {
    if (chapterCount == 0) return StoryPrimaryReadAction.NoChapters
    if (releaseTargets.isEmpty()) return StoryPrimaryReadAction.NoReleases
    if (!readerAvailabilityResolved) return StoryPrimaryReadAction.CheckingSources

    val validatedResumeTarget = resumeTarget?.takeIf(releaseTargets::contains)
    if (validatedResumeTarget != null) {
        return StoryPrimaryReadAction.Read(validatedResumeTarget, isResume = true)
    }
    readableTargets.firstOrNull()?.let { target ->
        return StoryPrimaryReadAction.Read(target, isResume = false)
    }
    return StoryPrimaryReadAction.FindSource
}
