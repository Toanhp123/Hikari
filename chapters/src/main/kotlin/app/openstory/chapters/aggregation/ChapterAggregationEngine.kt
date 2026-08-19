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

        val linkedChapterIds = links.mapTo(hashSetOf(), ChapterReleaseLink::canonicalChapterId)
        val tombstones = existing
            .filter { chapter -> !chapter.tombstoned && chapter.id !in linkedChapterIds }
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
        val best = bestCandidate(release, existing, creates.values)
        if (best != null && best.score.score >= policy.autoLinkThreshold) return best.chapter.id

        val proposed = if (release.parsedLabel.hasStrongIdentity()) {
            createShared(storyId, release, creates)
        } else {
            createUnique(storyId, release, creates)
        }
        if (best != null && best.score.score >= policy.reviewThreshold) {
            reviews += ChapterReviewCandidate(
                releaseId = release.id,
                proposedChapterId = proposed,
                candidateChapterId = best.chapter.id,
                score = best.score.score,
                policyVersion = policy.version,
            )
        }
        return proposed
    }


    private fun bestCandidate(
        release: ChapterRelease,
        existing: List<CanonicalChapter>,
        creates: Collection<CanonicalChapter>,
    ): RankedChapterCandidate? {
        var best: RankedChapterCandidate? = null
        existing.forEach { chapter -> best = betterCandidate(release, chapter, best) }
        creates.forEach { chapter -> best = betterCandidate(release, chapter, best) }
        return best
    }

    private fun betterCandidate(
        release: ChapterRelease,
        chapter: CanonicalChapter,
        current: RankedChapterCandidate?,
    ): RankedChapterCandidate? {
        val score = scorer.compare(release.parsedLabel, chapter.parsedLabel)
        return when {
            score.explicitConflict -> current
            current == null -> RankedChapterCandidate(chapter, score)
            else -> betterScoredCandidate(chapter, score, current)
        }
    }

    private fun betterScoredCandidate(
        chapter: CanonicalChapter,
        score: ChapterMatchScore,
        current: RankedChapterCandidate,
    ): RankedChapterCandidate {
        val scoreOrder = score.score.compareTo(current.score.score)
        val winsTieBreak = scoreOrder == 0 && chapter.id.value < current.chapter.id.value
        return if (scoreOrder > 0 || winsTieBreak) RankedChapterCandidate(chapter, score) else current
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

private data class RankedChapterCandidate(
    val chapter: CanonicalChapter,
    val score: ChapterMatchScore,
)

private fun ParsedChapterLabel.hasStrongIdentity(): Boolean = chapter != null || kind != ChapterKind.UNKNOWN

private fun ParsedChapterLabel.stableKey(): String = listOf(
    kind.name.lowercase(),
    volume.stableValue(),
    chapter.stableValue(),
    part?.toString() ?: "-",
).joinToString(":")

private fun BigDecimal?.stableValue(): String = this?.stripTrailingZeros()?.toPlainString() ?: "-"
