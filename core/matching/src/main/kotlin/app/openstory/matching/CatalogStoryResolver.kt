package app.openstory.matching

import app.openstory.model.CatalogCanonicalResolution
import app.openstory.model.CatalogCanonicalResolver
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.CanonicalStory
import app.openstory.model.PluginId
import app.openstory.model.StoryId

class CatalogStoryResolver(
    private val policy: CatalogMatchPolicy,
) : CatalogCanonicalResolver {
    override fun resolve(
        pluginId: PluginId,
        source: CatalogSnapshotItem,
        candidates: List<CanonicalStory>,
    ): CatalogCanonicalResolution {
        val ranked = candidates
            .map { candidate -> compare(pluginId, source, candidate) }
            .sortedWith(MATCH_RESULT_ORDERING)
        val best = ranked.firstOrNull()
        val second = ranked.drop(1).firstOrNull { it.decision == MergeDecision.AUTO_LINK }

        return if (best != null && canAutoLink(best, second)) {
            CatalogCanonicalResolution.Existing(best.storyId)
        } else {
            sourceIsolatedResolution(pluginId, source, candidates)
        }
    }

    fun compare(
        source: CatalogSnapshotItem,
        candidate: CanonicalStory,
    ): CatalogMatchResult = compareInternal(
        pluginId = null,
        source = source,
        candidate = candidate,
    )

    fun compare(
        pluginId: PluginId,
        source: CatalogSnapshotItem,
        candidate: CanonicalStory,
    ): CatalogMatchResult = compareInternal(
        pluginId = pluginId,
        source = source,
        candidate = candidate,
    )

    private fun compareInternal(
        pluginId: PluginId?,
        source: CatalogSnapshotItem,
        candidate: CanonicalStory,
    ): CatalogMatchResult {
        val contentTypeConflict = source.contentType != candidate.contentType
        val trustedDirectMapping = pluginId != null && hasTrustedDirectMapping(
            sourceIdentity = CatalogSourceIdentity(pluginId, source.sourceId),
            candidate = candidate,
        )
        val titleMatch = bestTitleMatch(source.title, candidate)
        val authorSimilarity = authorSimilarity(source, candidate)
        val authorConflict = authorSimilarity != null && authorSimilarity == 0.0
        val score = if (authorSimilarity == null) {
            titleMatch.similarity
        } else {
            titleMatch.similarity * TITLE_WEIGHT + authorSimilarity * AUTHOR_WEIGHT
        }
        val decision = decide(
            contentTypeConflict = contentTypeConflict,
            trustedDirectMapping = trustedDirectMapping,
            titleSimilarity = titleMatch.similarity,
            authorSimilarity = authorSimilarity,
        )

        return CatalogMatchResult(
            storyId = candidate.id,
            score = if (trustedDirectMapping && !contentTypeConflict) DIRECT_MAPPING_SCORE else score,
            decision = decision,
            explanation = CatalogMatchExplanation(
                titleSimilarity = titleMatch.similarity,
                matchedTitle = titleMatch.title,
                authorSimilarity = authorSimilarity,
                authorConflict = authorConflict,
                contentTypeConflict = contentTypeConflict,
                trustedDirectMapping = trustedDirectMapping,
            ),
        )
    }

    private fun decide(
        contentTypeConflict: Boolean,
        trustedDirectMapping: Boolean,
        titleSimilarity: Double,
        authorSimilarity: Double?,
    ): MergeDecision = when {
        contentTypeConflict -> MergeDecision.SEPARATE
        trustedDirectMapping -> MergeDecision.AUTO_LINK
        titleSimilarity >= policy.autoLinkTitleSimilarityAt &&
            authorSimilarity != null &&
            authorSimilarity >= policy.autoLinkAuthorSimilarityAt -> MergeDecision.AUTO_LINK
        titleSimilarity >= policy.reviewTitleSimilarityAt -> MergeDecision.REVIEW
        else -> MergeDecision.SEPARATE
    }

    private fun canAutoLink(
        best: CatalogMatchResult,
        second: CatalogMatchResult?,
    ): Boolean = when {
        best.decision != MergeDecision.AUTO_LINK -> false
        best.explanation.trustedDirectMapping -> true
        second == null -> true
        else -> best.score - second.score >= policy.minimumAutoLinkLead
    }

    private fun hasTrustedDirectMapping(
        sourceIdentity: CatalogSourceIdentity,
        candidate: CanonicalStory,
    ): Boolean {
        val targets = policy.trustedDirectMappings
            .asSequence()
            .filter { mapping -> mapping.source == sourceIdentity }
            .map(TrustedCatalogMapping::target)
            .toSet()
        if (targets.isEmpty()) return false

        return candidate.catalogEntries.any { entry ->
            CatalogSourceIdentity(entry.catalogPluginId, entry.externalStoryId) in targets
        }
    }

    private fun bestTitleMatch(
        sourceTitle: String,
        candidate: CanonicalStory,
    ): TitleMatch {
        val candidateTitles = buildSet {
            add(candidate.preferredTitle)
            addAll(candidate.aliases)
            candidate.catalogEntries.forEach { entry ->
                add(entry.title)
                addAll(entry.aliases)
            }
        }
        return candidateTitles
            .map { title -> TitleMatch(title, TitleNormalizer.similarity(sourceTitle, title)) }
            .sortedWith(
                compareByDescending<TitleMatch>(TitleMatch::similarity)
                    .thenBy { match -> TitleNormalizer.normalize(match.title) },
            )
            .first()
    }

    private fun authorSimilarity(
        source: CatalogSnapshotItem,
        candidate: CanonicalStory,
    ): Double? {
        val sourceAuthors = source.authors
            .map(TitleNormalizer::normalize)
            .filter(String::isNotBlank)
            .toSet()
        val candidateAuthors = candidate.catalogEntries
            .asSequence()
            .flatMap { entry -> entry.authors.asSequence() }
            .map(TitleNormalizer::normalize)
            .filter(String::isNotBlank)
            .toSet()
        return TitleNormalizer.setSimilarity(sourceAuthors, candidateAuthors)
    }

    private fun sourceIsolatedResolution(
        pluginId: PluginId,
        source: CatalogSnapshotItem,
        candidates: List<CanonicalStory>,
    ): CatalogCanonicalResolution {
        val storyId = StoryId("catalog:${pluginId.value}:${source.sourceId}")
        return if (candidates.any { candidate -> candidate.id == storyId }) {
            CatalogCanonicalResolution.Existing(storyId)
        } else {
            CatalogCanonicalResolution.Create(storyId)
        }
    }

    private data class TitleMatch(
        val title: String,
        val similarity: Double,
    )

    private companion object {
        const val TITLE_WEIGHT = 0.80
        const val AUTHOR_WEIGHT = 0.20
        const val DIRECT_MAPPING_SCORE = 1.0

        val MATCH_RESULT_ORDERING: Comparator<CatalogMatchResult> =
            compareBy<CatalogMatchResult> { result -> result.decision.ordinal }
                .thenByDescending { result -> result.explanation.trustedDirectMapping }
                .thenByDescending(CatalogMatchResult::score)
                .thenBy { result -> result.storyId.value }
    }
}
