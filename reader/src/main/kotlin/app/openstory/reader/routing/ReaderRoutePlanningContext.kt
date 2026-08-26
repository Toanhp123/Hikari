package app.openstory.reader.routing

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.preferences.ReaderPreferences

internal data class ReaderRoutePlanningContext(
    val storyId: StoryId,
    val targetChapterId: CanonicalChapterId,
    val chapterGraphRevision: ReaderChapterGraphRevision,
    val planRevision: ReaderPlanRevision,
    val chapterGraph: ReaderSessionChapterGraph,
    val preferences: ReaderPreferences,
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
