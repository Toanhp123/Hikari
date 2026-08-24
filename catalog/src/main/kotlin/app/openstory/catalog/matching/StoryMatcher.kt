package app.openstory.catalog.matching

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.Story
import app.openstory.common.id.StoryId
import java.security.MessageDigest

class StoryMatcher(private val policy: MatchPolicy = MatchPolicy()) {
    fun resolve(source: CatalogMatchCandidate, candidates: List<CatalogMatchCandidate>): StoryResolution {
        val direct = candidates.asSequence()
            .filter { candidate -> candidate.sourceKeys.any(source.sourceKeys::contains) }
            .minByOrNull { it.story.id.value }
        if (direct != null) return StoryResolution.Existing(direct.story.id)

        val preparedCandidates = candidates.map(::prepare)
        return resolvePrepared(
            prepare(source),
            preparedCandidates.asSequence(),
            preparedCandidates.asSequence().map { it.story.id },
        )
    }

    internal fun resolvePrepared(
        source: PreparedCatalogMatchCandidate,
        candidates: Sequence<PreparedCatalogMatchCandidate>,
        existingStoryIds: Sequence<StoryId>,
    ): StoryResolution {
        var best: CatalogMatchResult? = null
        var bestAutoLink: CatalogMatchResult? = null
        var secondAutoLink: CatalogMatchResult? = null

        candidates.forEach { candidate ->
            val result = comparePrepared(source, candidate)
            if (best == null || matchOrdering.compare(result, best) < 0) best = result
            if (result.decision == MergeDecision.AUTO_LINK) {
                if (bestAutoLink == null || matchOrdering.compare(result, bestAutoLink) < 0) {
                    secondAutoLink = bestAutoLink
                    bestAutoLink = result
                } else if (secondAutoLink == null || matchOrdering.compare(result, secondAutoLink) < 0) {
                    secondAutoLink = result
                }
            }
        }

        val accepted = best?.takeIf { candidate ->
            candidate.decision == MergeDecision.AUTO_LINK && hasSufficientLead(candidate, secondAutoLink)
        }
        return accepted?.let { StoryResolution.Existing(it.storyId) }
            ?: createStory(source, existingStoryIds)
    }

    internal fun canSkipZeroTitleOverlap(): Boolean = policy.autoLinkTitleSimilarityAt > 0.0

    private fun hasSufficientLead(
        best: CatalogMatchResult,
        second: CatalogMatchResult?,
    ): Boolean = second == null || best.score - second.score >= policy.minimumAutoLinkLead

    private fun createStory(
        source: PreparedCatalogMatchCandidate,
        existingStoryIds: Sequence<StoryId>,
    ): StoryResolution.Create {
        val semantic = listOf(
            source.story.contentType.name,
            source.normalizedTitleSignature.joinToString("|"),
            source.normalizedAuthorSignature.joinToString("|"),
            source.sourceKeys
                .map { "${it.pluginId.value}:${it.sourceId}" }
                .sorted()
                .joinToString("|"),
        ).joinToString("#")
        val existingIds = existingStoryIds.toHashSet()
        val base = "catalog:${digest(semantic)}"
        val id = generateSequence(1) { it + 1 }
            .map { suffix -> StoryId(if (suffix == 1) base else "$base:$suffix") }
            .first { candidateId -> candidateId !in existingIds }
        return StoryResolution.Create(Story(id, source.story.contentType))
    }

    fun compare(source: CatalogMatchCandidate, candidate: CatalogMatchCandidate): CatalogMatchResult =
        comparePrepared(prepare(source), prepare(candidate))

    internal fun prepare(candidate: CatalogMatchCandidate): PreparedCatalogMatchCandidate {
        val normalizedTitles = candidate.titles.asSequence()
            .map(TitleNormalizer::normalize)
            .filter(String::isNotBlank)
            .sorted()
            .toList()
        val normalizedAuthors = candidate.authors.asSequence()
            .map(TitleNormalizer::normalize)
            .filter(String::isNotBlank)
            .sorted()
            .toList()
        val preparedEvidence = candidate.evidence.map(::prepareEvidence)
        return PreparedCatalogMatchCandidate(
            story = candidate.story,
            evidence = preparedEvidence,
            normalizedTitleSignature = normalizedTitles,
            normalizedAuthorSignature = normalizedAuthors,
            sourceKeys = candidate.sourceKeys,
            titleTokens = preparedEvidence.asSequence()
                .flatMap { evidence -> evidence.titles.asSequence() }
                .flatMap { title -> title.tokens.asSequence() }
                .toSet(),
        )
    }

    private fun prepareEvidence(evidence: CatalogMatchEvidence): PreparedCatalogMatchEvidence =
        PreparedCatalogMatchEvidence(
            titles = evidence.titles.map { title ->
                val normalized = TitleNormalizer.normalize(title)
                PreparedTitle(normalized, TitleNormalizer.tokensOfNormalized(normalized))
            },
            normalizedAuthors = evidence.authors.asSequence()
                .map(TitleNormalizer::normalize)
                .filter(String::isNotBlank)
                .toSet(),
            contentType = evidence.contentType,
        )

    private fun comparePrepared(
        source: PreparedCatalogMatchCandidate,
        candidate: PreparedCatalogMatchCandidate,
    ): CatalogMatchResult {
        var best: CatalogMatchResult? = null
        source.evidence.forEach { sourceEvidence ->
            candidate.evidence.forEach { candidateEvidence ->
                val result = compareEvidence(
                    sourceEvidence,
                    candidateEvidence,
                    candidate.story.id,
                )
                if (best == null || matchOrdering.compare(result, best) < 0) best = result
            }
        }
        return best ?: CatalogMatchResult(
            candidate.story.id,
            0.0,
            MergeDecision.SEPARATE,
        )
    }

    private fun compareEvidence(
        source: PreparedCatalogMatchEvidence,
        candidate: PreparedCatalogMatchEvidence,
        storyId: StoryId,
    ): CatalogMatchResult {
        val contentConflict = source.contentType != candidate.contentType
        var titleScore = 0.0
        candidate.titles.forEach { candidateTitle ->
            source.titles.forEach { sourceTitle ->
                val score = TitleNormalizer.similarityNormalized(
                    sourceTitle.normalized,
                    sourceTitle.tokens,
                    candidateTitle.normalized,
                    candidateTitle.tokens,
                )
                if (score > titleScore) titleScore = score
            }
        }
        val author = TitleNormalizer.setSimilarity(source.normalizedAuthors, candidate.normalizedAuthors)
        val score = if (author == null) titleScore else titleScore * TITLE_WEIGHT + author * AUTHOR_WEIGHT
        val decision = when {
            contentConflict -> MergeDecision.SEPARATE
            titleScore >= policy.autoLinkTitleSimilarityAt &&
                author != null &&
                author >= policy.autoLinkAuthorSimilarityAt -> MergeDecision.AUTO_LINK
            titleScore >= policy.reviewTitleSimilarityAt -> MergeDecision.REVIEW
            else -> MergeDecision.SEPARATE
        }
        return CatalogMatchResult(
            storyId,
            score,
            decision,
        )
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(DIGEST_BYTES)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIGEST_BYTES = 8
        const val TITLE_WEIGHT = 0.8
        const val AUTHOR_WEIGHT = 0.2
        val matchOrdering: Comparator<CatalogMatchResult> =
            compareBy<CatalogMatchResult> { it.decision.ordinal }
                .thenByDescending { it.score }
                .thenBy { it.storyId.value }
    }
}

internal data class PreparedCatalogMatchCandidate(
    val story: Story,
    val evidence: List<PreparedCatalogMatchEvidence>,
    val normalizedTitleSignature: List<String>,
    val normalizedAuthorSignature: List<String>,
    val sourceKeys: Set<SourceKey>,
    val titleTokens: Set<String>,
)

internal data class PreparedCatalogMatchEvidence(
    val titles: List<PreparedTitle>,
    val normalizedAuthors: Set<String>,
    val contentType: app.openstory.catalog.model.ContentType,
)

internal data class PreparedTitle(
    val normalized: String,
    val tokens: Set<String>,
)
