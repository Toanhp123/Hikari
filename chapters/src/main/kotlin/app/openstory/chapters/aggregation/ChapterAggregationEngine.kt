package app.openstory.chapters.aggregation

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import java.math.BigDecimal

class ChapterAggregationEngine(
    private val scorer: ChapterMatchScorer = ChapterMatchScorer(),
    private val policy: ChapterMatchPolicy = ChapterMatchPolicy(),
) {
    fun plan(
        storyId: StoryId,
        existing: List<CanonicalChapter>,
        releases: List<ChapterRelease>,
        overrides: List<ChapterAggregationOverride>,
    ): AggregationPlan {
        val overrideByRelease = overrides.associateBy(ChapterAggregationOverride::releaseId)
        val creates = linkedMapOf<CanonicalChapterId, CanonicalChapter>()
        val links = mutableListOf<ChapterReleaseLink>()
        val unlinks = linkedSetOf<ChapterReleaseId>()
        val reviews = mutableListOf<ChapterReviewCandidate>()

        releases.sortedBy { it.id.value }.forEach { release ->
            val override = overrideByRelease[release.id]
            val target = when (override?.kind) {
                ChapterOverrideKind.FORCE_LINK -> override.canonicalChapterId
                ChapterOverrideKind.FORCE_SEPARATE -> createUnique(storyId, release, creates)
                null -> automaticTarget(storyId, release, existing, creates, reviews)
            } ?: createUnique(storyId, release, creates)

            if (release.canonicalChapterId != null && release.canonicalChapterId != target) {
                unlinks += release.id
            }
            links += ChapterReleaseLink(release.id, target)
        }

        val activeReleaseIds = releases.mapTo(hashSetOf(), ChapterRelease::id)
        val tombstones = existing
            .filter { chapter ->
                chapter.releaseIds.isNotEmpty() && chapter.releaseIds.none(activeReleaseIds::contains)
            }
            .mapTo(sortedSetOf(compareBy(CanonicalChapterId::value)), CanonicalChapter::id)

        return AggregationPlan(
            creates = creates.values.sortedBy { it.id.value },
            links = links.sortedBy { it.releaseId.value },
            unlinks = unlinks,
            tombstones = tombstones,
            reviewCandidates = reviews.sortedWith(
                compareBy<ChapterReviewCandidate> { it.releaseId.value }
                    .thenBy { it.candidateChapterId.value },
            ),
        )
    }

    private fun automaticTarget(
        storyId: StoryId,
        release: ChapterRelease,
        existing: List<CanonicalChapter>,
        creates: MutableMap<CanonicalChapterId, CanonicalChapter>,
        reviews: MutableList<ChapterReviewCandidate>,
    ): CanonicalChapterId {
        val candidates = (existing + creates.values)
            .map { chapter -> chapter to scorer.compare(release.parsedLabel, chapter.parsedLabel) }
            .filterNot { (_, score) -> score.explicitConflict }
            .sortedWith(compareByDescending<Pair<CanonicalChapter, ChapterMatchScore>> { it.second.score }
                .thenBy { it.first.id.value })
        val best = candidates.firstOrNull()
        if (best != null && best.second.score >= policy.autoLinkThreshold) return best.first.id

        val proposed = if (release.parsedLabel.hasStrongIdentity()) {
            createShared(storyId, release, creates)
        } else {
            createUnique(storyId, release, creates)
        }
        if (best != null && best.second.score >= policy.reviewThreshold) {
            reviews += ChapterReviewCandidate(
                releaseId = release.id,
                proposedChapterId = proposed,
                candidateChapterId = best.first.id,
                score = best.second.score,
                policyVersion = policy.version,
            )
        }
        return proposed
    }

    private fun createShared(
        storyId: StoryId,
        release: ChapterRelease,
        creates: MutableMap<CanonicalChapterId, CanonicalChapter>,
    ): CanonicalChapterId = create(storyId, release, release.parsedLabel.stableKey(), creates)

    private fun createUnique(
        storyId: StoryId,
        release: ChapterRelease,
        creates: MutableMap<CanonicalChapterId, CanonicalChapter>,
    ): CanonicalChapterId = create(storyId, release, "release:${release.id.value}", creates)

    private fun create(
        storyId: StoryId,
        release: ChapterRelease,
        key: String,
        creates: MutableMap<CanonicalChapterId, CanonicalChapter>,
    ): CanonicalChapterId {
        val id = CanonicalChapterId("${storyId.value}:chapter:$key")
        creates.getOrPut(id) {
            CanonicalChapter(id, storyId, release.parsedLabel, release.displayLabel, false)
        }
        return id
    }
}

private fun ParsedChapterLabel.hasStrongIdentity(): Boolean = chapter != null || kind != ChapterKind.UNKNOWN

private fun ParsedChapterLabel.stableKey(): String = listOf(
    kind.name.lowercase(),
    volume.stableValue(),
    chapter.stableValue(),
    part?.toString() ?: "-",
).joinToString(":")

private fun BigDecimal?.stableValue(): String = this?.stripTrailingZeros()?.toPlainString() ?: "-"
