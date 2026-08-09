package app.openstory.catalog.matching

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.StoryId
import java.security.MessageDigest

class StoryMatcher(private val policy: MatchPolicy = MatchPolicy()) {
    fun resolve(source: CatalogMatchCandidate, candidates: List<CatalogMatchCandidate>): StoryResolution {
        val direct = candidates.firstOrNull { it.sourceKeys.intersect(source.sourceKeys).isNotEmpty() }
        if (direct != null) return StoryResolution.Existing(direct.story.id)

        val ranked = candidates.map { compare(source, it) }
            .sortedWith(compareBy<CatalogMatchResult> { it.decision.ordinal }.thenByDescending { it.score }.thenBy { it.storyId.value })
        val best = ranked.firstOrNull()
        val second = ranked.drop(1).firstOrNull { it.decision == MergeDecision.AUTO_LINK }
        if (best != null && best.decision == MergeDecision.AUTO_LINK &&
            (second == null || best.score - second.score >= policy.minimumAutoLinkLead)
        ) return StoryResolution.Existing(best.storyId)

        val semantic = source.titles.map(TitleNormalizer::normalize).filter(String::isNotBlank).sorted().firstOrNull()
            ?: source.sourceKeys.map { "${it.pluginId.value}:${it.sourceId}" }.sorted().first()
        val id = StoryId("catalog:${digest(semantic)}")
        val existing = candidates.firstOrNull { it.story.id == id }
        return if (existing != null) StoryResolution.Existing(id)
        else StoryResolution.Create(Story(id, source.story.contentType))
    }

    fun compare(source: CatalogMatchCandidate, candidate: CatalogMatchCandidate): CatalogMatchResult {
        val title = (source.titles + candidate.titles).map { it to source.titles.maxOf { s -> TitleNormalizer.similarity(s, it) } }
            .maxWithOrNull(compareBy<Pair<String, Double>> { it.second }.thenBy { TitleNormalizer.normalize(it.first) })
            ?: ("" to 0.0)
        val sourceAuthors = source.authors.map(TitleNormalizer::normalize).filter(String::isNotBlank).toSet()
        val candidateAuthors = candidate.authors.map(TitleNormalizer::normalize).filter(String::isNotBlank).toSet()
        val author = TitleNormalizer.setSimilarity(sourceAuthors, candidateAuthors)
        val conflict = author == 0.0
        val contentConflict = source.story.contentType != candidate.story.contentType
        val score = if (author == null) title.second else title.second * 0.8 + author * 0.2
        val decision = when {
            contentConflict -> MergeDecision.SEPARATE
            title.second >= policy.autoLinkTitleSimilarityAt && author != null && author >= policy.autoLinkAuthorSimilarityAt -> MergeDecision.AUTO_LINK
            title.second >= policy.reviewTitleSimilarityAt -> MergeDecision.REVIEW
            else -> MergeDecision.SEPARATE
        }
        return CatalogMatchResult(candidate.story.id, score, decision, CatalogMatchExplanation(title.second, title.first, author, conflict, contentConflict))
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(8)
        .joinToString("") { "%02x".format(it) }
}
