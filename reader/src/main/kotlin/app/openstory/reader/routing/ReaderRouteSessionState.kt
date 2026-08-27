package app.openstory.reader.routing

import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.engine.ReaderPlanRevision

internal data class ReaderSessionActiveExecution(
    val generationId: ReaderGenerationId,
    val targetChapterId: CanonicalChapterId,
    val explicitReleaseId: ChapterReleaseId?,
    val planRevision: ReaderPlanRevision,
)

internal data class ReaderSessionActivePlan(
    val identity: ReaderExecutionIdentity,
    val winnerReleaseId: ChapterReleaseId,
    val plannedReleaseIds: Set<ChapterReleaseId>,
)

internal fun readerGraphHardInvalidates(
    active: ReaderSessionActiveExecution,
    activeIdentity: ReaderExecutionIdentity,
    plan: ReaderSessionActivePlan?,
    nextGraph: ReaderSessionChapterGraph,
): Boolean {
    val target = nextGraph.group(active.targetChapterId)
    if (!target.hasRoutableTargetReleases()) return true
    return plan != null &&
        plan.identity == activeIdentity &&
        readerGraphInvalidatesPlan(active.targetChapterId, plan, nextGraph)
}

internal fun readerGraphInvalidatesPlan(
    targetChapterId: CanonicalChapterId,
    plan: ReaderSessionActivePlan,
    nextGraph: ReaderSessionChapterGraph,
): Boolean {
    val targetReleaseIds = nextGraph.group(targetChapterId)
        ?.takeUnless { group -> group.chapter.tombstoned }
        ?.let { group ->
            group.releases
                .asSequence()
                .filter { release -> release.canonicalChapterId == group.chapter.id }
                .mapTo(hashSetOf()) { release -> release.id }
        }
        .orEmpty()
    return targetReleaseIds.isEmpty() ||
        plan.winnerReleaseId !in targetReleaseIds ||
        plan.plannedReleaseIds.any { it !in targetReleaseIds }
}

private fun CanonicalChapterGroup?.hasRoutableTargetReleases(): Boolean = this?.let { group ->
    !group.chapter.tombstoned &&
        group.releases.any { release -> release.canonicalChapterId == group.chapter.id }
} == true
