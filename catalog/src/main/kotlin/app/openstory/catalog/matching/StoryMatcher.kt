package app.openstory.catalog.matching

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.StoryId
import java.security.MessageDigest

class StoryMatcher(private val policy: MatchPolicy = MatchPolicy()) {
    fun resolve(source: CatalogMatchCandidate, candidates: List<CatalogMatchCandidate>): StoryResolution {
        val direct = candidates
            .filter { it.sourceKeys.intersect(source.sourceKeys).isNotEmpty() }
            .minByOrNull { it.story.id.value }
        return direct?.let { StoryResolution.Existing(it.story.id) }
            ?: resolveByEvidence(source, candidates)
    }

    private fun resolveByEvidence(
        source: CatalogMatchCandidate,
        candidates: List<CatalogMatchCandidate>,
    ): StoryResolution {
        val ranked = candidates.map { compare(source, it) }.sortedWith(matchOrdering)
        val best = ranked.firstOrNull()
        val second = ranked.drop(1).firstOrNull {
            it.decision == MergeDecision.AUTO_LINK
        }
        val accepted = best?.takeIf { candidate ->
            candidate.decision == MergeDecision.AUTO_LINK && hasSufficientLead(candidate, second)
        }
        return accepted?.let { StoryResolution.Existing(it.storyId) }
            ?: createStory(source, candidates)
    }

    private fun hasSufficientLead(
        best: CatalogMatchResult,
        second: CatalogMatchResult?,
    ): Boolean = second == null || best.score - second.score >= policy.minimumAutoLinkLead

    private fun createStory(
        source: CatalogMatchCandidate,
        candidates: List<CatalogMatchCandidate>,
    ): StoryResolution.Create {
        val semantic = listOf(
            source.story.contentType.name,
            source.titles.normalizedSignature(),
            source.authors.normalizedSignature(),
            source.sourceKeys
                .map { "${it.pluginId.value}:${it.sourceId}" }
                .sorted()
                .joinToString("|"),
        ).joinToString("#")
        val base = "catalog:${digest(semantic)}"
        val id = generateSequence(1) { it + 1 }
            .map { suffix -> StoryId(if (suffix == 1) base else "$base:$suffix") }
            .first { candidateId -> candidates.none { it.story.id == candidateId } }
        return StoryResolution.Create(Story(id, source.story.contentType))
    }

    fun compare(source: CatalogMatchCandidate, candidate: CatalogMatchCandidate): CatalogMatchResult {
        val title = candidate.titles.flatMap { candidateTitle ->
            source.titles.map { sourceTitle ->
                candidateTitle to TitleNormalizer.similarity(sourceTitle, candidateTitle)
            }
        }
            .maxWithOrNull(
                compareBy<Pair<String, Double>> { it.second }
                    .thenByDescending { TitleNormalizer.normalize(it.first) },
            )
            ?: ("" to 0.0)
        val sourceAuthors = source.authors.map(TitleNormalizer::normalize).filter(String::isNotBlank).toSet()
        val candidateAuthors = candidate.authors.map(TitleNormalizer::normalize).filter(String::isNotBlank).toSet()
        val author = TitleNormalizer.setSimilarity(sourceAuthors, candidateAuthors)
        val conflict = author == 0.0
        val contentConflict = source.story.contentType != candidate.story.contentType
        val score = if (author == null) title.second else title.second * 0.8 + author * 0.2
        val decision = when {
            contentConflict -> MergeDecision.SEPARATE
            title.second >= policy.autoLinkTitleSimilarityAt &&
                author != null &&
                author >= policy.autoLinkAuthorSimilarityAt -> MergeDecision.AUTO_LINK
            title.second >= policy.reviewTitleSimilarityAt -> MergeDecision.REVIEW
            else -> MergeDecision.SEPARATE
        }
        return CatalogMatchResult(
            candidate.story.id,
            score,
            decision,
            CatalogMatchExplanation(title.second, title.first, author, conflict, contentConflict),
        )
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(DIGEST_BYTES)
        .joinToString("") { "%02x".format(it) }

    private fun Set<String>.normalizedSignature(): String = map(TitleNormalizer::normalize)
        .filter(String::isNotBlank)
        .sorted()
        .joinToString("|")

    private companion object {
        const val DIGEST_BYTES = 8
        val matchOrdering: Comparator<CatalogMatchResult> =
            compareBy<CatalogMatchResult> { it.decision.ordinal }
                .thenByDescending { it.score }
                .thenBy { it.storyId.value }
    }
}
