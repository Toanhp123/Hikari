package app.openstory.reader.routing

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderPlanRevision

internal data class ReaderRoutingPreferences(
    val languageOrder: List<String>,
) {
    companion object {
        fun create(languageOrder: List<String>) = ReaderRoutingPreferences(languageOrder.toList())
    }
}

internal data class ReaderRoutePlanningContext(
    val storyId: StoryId,
    val targetChapterId: CanonicalChapterId,
    val chapterGraphRevision: ReaderChapterGraphRevision,
    val planRevision: ReaderPlanRevision,
    val chapterGraph: ReaderSessionChapterGraph,
    val preferences: ReaderRoutingPreferences,
    val committedIdentity: ReaderCommittedIdentity?,
    val explicitReleaseId: ChapterReleaseId?,
    val knownInvalidLocalFingerprints: Map<ChapterReleaseId, Set<String>> = emptyMap(),
)

internal fun ReaderRouteExecutionContext.toPlanningContext() = ReaderRoutePlanningContext(
    storyId = storyId,
    targetChapterId = identity.targetChapterId,
    chapterGraphRevision = chapterGraphRevision,
    planRevision = identity.planRevision,
    chapterGraph = chapterGraph,
    preferences = preferences,
    committedIdentity = committedIdentity,
    explicitReleaseId = explicitReleaseId,
    knownInvalidLocalFingerprints = knownInvalidLocalFingerprints,
)
