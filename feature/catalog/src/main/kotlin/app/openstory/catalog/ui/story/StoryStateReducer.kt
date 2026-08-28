package app.openstory.catalog.ui.story

import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.Score
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.ObservationState
import app.openstory.catalog.ui.state.RefreshState
import app.openstory.catalog.ui.state.forExpectedKey
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryStatus
import app.openstory.reader.progress.ReadingProgress

internal data class StoryObservations(
    val canonical: StoryCanonicalReadiness,
    val library: ObservationState<StoryId, LibraryStatus?>,
    val progress: ObservationState<StoryId, ReaderTarget?>,
)

internal data class StoryControls(
    val selectedSource: SourceKey?,
    val refresh: RefreshState,
    val commandFailure: CatalogUiFailure?,
    val selectedSection: StorySection,
)

internal fun reduceStoryState(
    observations: StoryObservations,
    controls: StoryControls,
    review: StoryReconciliationState,
): StoryUiState {
    val canonicalContent = observations.canonical.content
    val resolvedId = observations.canonical.resolvedStoryId
    val libraryState = observations.library.forExpectedKey(resolvedId)
    val progressState = observations.progress.forExpectedKey(resolvedId)
    val readyCanonical = (canonicalContent as? ContentState.Ready)?.value
    val sources = readyCanonical?.sources.orEmpty().map { it.entry }.sortedWith(
        compareBy<CatalogEntry> { it.pluginId.value }.thenBy { it.sourceId },
    )
    val inspection = controls.selectedSource?.takeIf { key -> sources.any { it.matches(key) } }
    val content = when (canonicalContent) {
        is ContentState.Pending -> ContentState.Pending
        is ContentState.Failed -> canonicalContent
        is ContentState.Ready -> ContentState.Ready(canonicalContent.value.toStoryUiModel(sources))
    }
    val libraryStatus = (libraryState as? ObservationState.Available)?.value
    val libraryStatusResolved = libraryState is ObservationState.Available
    val resumeTarget = (progressState as? ObservationState.Available)?.value
    return StoryUiState(
        storyId = resolvedId,
        content = content,
        selectedSource = inspection?.toIdentity(),
        refresh = controls.refresh,
        observationIssue = observations.observationIssue(content, libraryState, progressState),
        commandFailure = controls.commandFailure,
        libraryStatus = libraryStatus,
        libraryStatusResolved = libraryStatusResolved,
        resumeTarget = resumeTarget,
        selectedSection = controls.selectedSection,
        reconciliationPrompt = review.prompt,
        reconciliationResolving = review.resolving,
        reconciliationFailureMessage = review.failureMessage,
    )
}

private fun StoryObservations.observationIssue(
    content: ContentState<StoryUiModel>,
    libraryState: ObservationState<StoryId, LibraryStatus?>,
    progressState: ObservationState<StoryId, ReaderTarget?>,
): CatalogUiFailure? = if (content !is ContentState.Ready) {
    null
} else {
    listOfNotNull(
        canonical.routeObservation.issueOrFailure(),
        libraryState.issueOrFailure(),
        progressState.issueOrFailure(),
    ).firstOrNull()
}

private fun ObservationState<*, *>.issueOrFailure(): CatalogUiFailure? = when (this) {
    is ObservationState.Available -> issue
    is ObservationState.Unavailable -> failure
    is ObservationState.Pending -> null
}

private fun CatalogEntry.matches(key: SourceKey): Boolean = pluginId == key.pluginId && sourceId == key.sourceId
private fun SourceKey.toIdentity() = StorySourceIdentity(pluginId, sourceId)

internal fun CanonicalStoryState.Ready.toStoryUiModel(rawSources: List<CatalogEntry>): StoryUiModel {
    val canonicalScore = generation.metadata.score
    return StoryUiModel(
        storyId = story.id,
        preferredTitle = generation.metadata.title,
        contentType = story.contentType,
        aliases = generation.metadata.aliases.toSet(),
        description = generation.metadata.description,
        coverUrl = generation.metadata.coverUrl,
        score = canonicalScore?.let { Score(it.normalizedValue * PRESENTATION_SCORE_SCALE, PRESENTATION_SCORE_SCALE) },
        authors = generation.metadata.authors.toSet(),
        genres = generation.metadata.genres.toSet(),
        languageTags = generation.metadata.languageTags.toSet(),
        sources = rawSources,
        effectivePrimary = generation.effectivePrimary,
        preferenceMode = preference.mode,
        pinnedSource = preference.pinnedSource,
        publicationStatus = generation.metadata.publicationStatus,
    )
}

internal fun List<ReadingProgress>.latestResumeTarget(storyId: StoryId): ReaderTarget? =
    asSequence().filter { it.storyId == storyId && it.completedAtEpochMillis == null }
        .maxWithOrNull(compareBy<ReadingProgress> { it.updatedAtEpochMillis }.thenBy { it.releaseId.value })
        ?.let { ReaderTarget(storyId, it.canonicalChapterId, it.releaseId) }

private const val PRESENTATION_SCORE_SCALE = 10.0
