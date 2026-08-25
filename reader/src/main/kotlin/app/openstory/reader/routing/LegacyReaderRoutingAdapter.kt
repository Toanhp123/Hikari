package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.CanonicalChapterId
import app.openstory.reader.engine.BasisPoints
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.CandidateRemoteAccess
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderNetworkClass
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot
import app.openstory.reader.engine.ReadingContinuity
import app.openstory.reader.engine.RoutingCandidate
import app.openstory.reader.engine.RoutingIntent
import app.openstory.reader.engine.SourceGroupKey
import app.openstory.reader.selection.ReleaseCandidate
import app.openstory.reader.selection.ReleaseSelectionPolicy

/**
 * Temporary M1 migration adapter from the legacy Reader selector model into pure HES facts.
 *
 * Engine DTOs intentionally stay internal to :reader. Production mapping only forwards facts
 * currently owned by ChapterRelease; synthetic source-group/completeness facts are restricted to
 * differential migration fixtures.
 */
internal object LegacyReaderRoutingAdapter {
    fun productionCandidate(
        release: ChapterRelease,
        remoteAccess: CandidateRemoteAccess,
    ): RoutingCandidate = RoutingCandidate(
        releaseId = release.id,
        sourceId = release.pluginId,
        languageTag = release.languageTag,
        sourceGroupKey = null,
        publishedAtEpochMillis = release.publishedAtEpochMillis,
        completeness = BasisPoints(10_000),
        remoteAccess = remoteAccess,
        localAccess = CandidateLocalAccess.Unknown,
    )

    fun differentialCandidate(candidate: ReleaseCandidate): RoutingCandidate = RoutingCandidate(
        releaseId = candidate.release.id,
        sourceId = candidate.release.pluginId,
        languageTag = candidate.release.languageTag,
        sourceGroupKey = candidate.sourceGroup?.let(::SourceGroupKey),
        publishedAtEpochMillis = candidate.release.publishedAtEpochMillis,
        completeness = BasisPoints(candidate.completeness * 100),
        remoteAccess = CandidateRemoteAccess.PERMITTED,
        localAccess = CandidateLocalAccess.Miss,
    )

    fun compatibilityPolicy(selectionPolicy: ReleaseSelectionPolicy): ReaderRoutingPolicy =
        ReaderRoutingPolicy.v1(languageOrder = selectionPolicy.languageOrder)

    fun compatibilitySnapshot(
        targetChapterId: CanonicalChapterId,
        candidates: Collection<ReleaseCandidate>,
        selectionPolicy: ReleaseSelectionPolicy,
        chapterGraphRevision: ReaderChapterGraphRevision = ReaderChapterGraphRevision(0),
        planRevision: ReaderPlanRevision = ReaderPlanRevision(0),
    ): ReaderRoutingSnapshot = ReaderRoutingSnapshot.create(
        targetChapterId = targetChapterId,
        chapterGraphRevision = chapterGraphRevision,
        planRevision = planRevision,
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = candidates.map(::differentialCandidate),
        sourceHealth = emptyList(),
        continuity = ReadingContinuity(
            committedSourceId = selectionPolicy.previousPluginId,
            committedSourceGroupKey = selectionPolicy.previousSourceGroup?.let(::SourceGroupKey),
            targetResumeReleaseId = selectionPolicy.previousReleaseId,
        ),
        networkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId = selectionPolicy.explicitReleaseId,
        nowEpochMillis = 0L,
    )
}
