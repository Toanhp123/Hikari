package app.openstory.chapters.aggregation

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId

data class ChapterReleaseLink(
    val releaseId: ChapterReleaseId,
    val canonicalChapterId: CanonicalChapterId,
)

data class ChapterReviewCandidate(
    val releaseId: ChapterReleaseId,
    val proposedChapterId: CanonicalChapterId,
    val candidateChapterId: CanonicalChapterId,
    val score: Double,
    val policyVersion: Int,
)

data class AggregationPlan(
    val creates: List<CanonicalChapter>,
    val links: List<ChapterReleaseLink>,
    val unlinks: Set<ChapterReleaseId>,
    val tombstones: Set<CanonicalChapterId>,
    val reviewCandidates: List<ChapterReviewCandidate>,
)
