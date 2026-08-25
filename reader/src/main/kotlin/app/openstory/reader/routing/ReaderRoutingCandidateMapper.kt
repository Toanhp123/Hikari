package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterRelease
import app.openstory.reader.engine.BasisPoints
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.CandidateRemoteAccess
import app.openstory.reader.engine.RoutingCandidate

/** Maps production Chapter facts into the pure HES-v1 candidate contract without inventing facts. */
internal object ReaderRoutingCandidateMapper {
    fun productionCandidate(
        release: ChapterRelease,
        remoteAccess: CandidateRemoteAccess,
        localAccess: CandidateLocalAccess = CandidateLocalAccess.Unknown,
    ): RoutingCandidate = RoutingCandidate(
        releaseId = release.id,
        sourceId = release.pluginId,
        languageTag = release.languageTag,
        sourceGroupKey = null,
        publishedAtEpochMillis = release.publishedAtEpochMillis,
        completeness = BasisPoints(BasisPoints.MAX_VALUE),
        remoteAccess = remoteAccess,
        localAccess = localAccess,
    )
}
