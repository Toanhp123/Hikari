package app.openstory.reader.routing

import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.selection.ReleaseCandidate
import app.openstory.reader.selection.ReleaseSelectionPolicy

internal data class AssembledRouteSnapshot(
    val targetIndex: Int,
    val targetGroup: CanonicalChapterGroup,
    val candidates: List<ReleaseCandidate>,
    val expectedFingerprints: Map<ChapterReleaseId, String>,
    val restoredProgress: ReadingProgress?,
    val snapshot: ReaderRoutingSnapshot,
    val policy: ReaderRoutingPolicy,
)

internal class RouteSnapshotAssembler(
    private val progress: ReadingProgressRepository,
) {
    suspend fun assemble(context: ReaderRouteExecutionContext): AssembledRouteSnapshot? {
        val targetIndex = context.chapterGroups.indexOfFirst {
            it.chapter.id == context.identity.targetChapterId
        }
        if (targetIndex < 0) return null

        val targetGroup = context.chapterGroups[targetIndex]
        val restored = progress.find(context.storyId, context.identity.targetChapterId)
        val candidates = targetGroup.releases.map(::ReleaseCandidate)
        val previousPluginId = restored?.releaseId?.let { releaseId ->
            targetGroup.releases.firstOrNull { it.id == releaseId }?.pluginId
        }
        val selectionPolicy = ReleaseSelectionPolicy(
            explicitReleaseId = context.explicitReleaseId,
            previousReleaseId = restored?.releaseId,
            previousPluginId = previousPluginId,
            languageOrder = context.preferences.languageOrder,
        )
        val expectedFingerprints = restored?.let {
            mapOf(it.releaseId to it.contentFingerprint)
        }.orEmpty()
        return AssembledRouteSnapshot(
            targetIndex = targetIndex,
            targetGroup = targetGroup,
            candidates = candidates,
            expectedFingerprints = expectedFingerprints,
            restoredProgress = restored,
            snapshot = LegacyReaderRoutingAdapter.compatibilitySnapshot(
                targetChapterId = context.identity.targetChapterId,
                candidates = candidates,
                selectionPolicy = selectionPolicy,
                chapterGraphRevision = context.chapterGraphRevision,
                planRevision = context.identity.planRevision,
            ),
            policy = LegacyReaderRoutingAdapter.compatibilityPolicy(selectionPolicy),
        )
    }
}
