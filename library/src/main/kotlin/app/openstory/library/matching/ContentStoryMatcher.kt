package app.openstory.library.matching

import java.text.Normalizer
import java.util.Locale

enum class ContentMatchDecision {
    AUTO_LINK,
    REVIEW,
    REJECT,
}

data class ContentTitleEvidence(
    val similarity: Double,
    val canonicalTitle: String,
    val candidateTitle: String,
)

data class ContentMatchExplanation(
    val directEvidence: Boolean,
    val title: ContentTitleEvidence,
    val authorSimilarity: Double?,
    val authorConflict: Boolean,
    val contentTypeMatch: Boolean?,
    val contentTypeConflict: Boolean,
    val reasons: List<String>,
)

data class ContentMatchResult(
    val score: Double,
    val decision: ContentMatchDecision,
    val explanation: ContentMatchExplanation,
    val policyVersion: Int,
)

class ContentStoryMatcher(
    private val policy: ContentMatchPolicy = ContentMatchPolicy(),
) {
    fun compare(
        canonical: ContentStoryFeatures,
        candidate: ContentStoryFeatures,
    ): ContentMatchResult {
        val signals = signals(canonical, candidate)
        val score = weightedScore(signals)
        val decision = decide(score, signals)
        return ContentMatchResult(
            score = score,
            decision = decision,
            explanation = signals.explanation(decision),
            policyVersion = policy.version,
        )
    }

    private fun signals(
        canonical: ContentStoryFeatures,
        candidate: ContentStoryFeatures,
    ): MatchSignals {
        val title = bestTitleEvidence(canonical.titles, candidate.titles)
        val authorSimilarity = setSimilarity(
            canonical.authors.normalizedValues(),
            candidate.authors.normalizedValues(),
        )
        val contentTypeMatch = when {
            canonical.contentType == null || candidate.contentType == null -> null
            else -> canonical.contentType == candidate.contentType
        }
        return MatchSignals(
            title = title,
            authorSimilarity = authorSimilarity,
            authorConflict = authorSimilarity == 0.0,
            contentTypeMatch = contentTypeMatch,
            contentTypeConflict = contentTypeMatch == false,
            directEvidence = canonical.directMappings.intersect(candidate.directMappings).isNotEmpty(),
        )
    }

    private fun decide(score: Double, signals: MatchSignals): ContentMatchDecision = when {
        signals.contentTypeConflict -> ContentMatchDecision.REJECT
        signals.directEvidence -> ContentMatchDecision.AUTO_LINK
        signals.authorConflict && signals.title.similarity >= policy.reviewAt -> ContentMatchDecision.REVIEW
        signals.authorConflict -> ContentMatchDecision.REJECT
        score >= policy.autoLinkAt &&
            signals.title.similarity >= policy.minimumAutoLinkTitleSimilarity &&
            (signals.authorSimilarity == null ||
                signals.authorSimilarity >= policy.minimumAutoLinkAuthorSimilarity) -> ContentMatchDecision.AUTO_LINK
        score >= policy.reviewAt -> ContentMatchDecision.REVIEW
        else -> ContentMatchDecision.REJECT
    }

    private fun weightedScore(signals: MatchSignals): Double {
        if (signals.directEvidence) return 1.0
        var weighted = signals.title.similarity * policy.titleWeight
        var weights = policy.titleWeight
        signals.authorSimilarity?.let { similarity ->
            weighted += similarity * policy.authorWeight
            weights += policy.authorWeight
        }
        signals.contentTypeMatch?.let { matches ->
            weighted += (if (matches) 1.0 else 0.0) * policy.contentTypeWeight
            weights += policy.contentTypeWeight
        }
        return weighted / weights
    }

    private fun bestTitleEvidence(
        canonicalTitles: Set<String>,
        candidateTitles: Set<String>,
    ): TitleEvidence = canonicalTitles
        .flatMap { canonical ->
            candidateTitles.map { candidate ->
                TitleEvidence(canonical, candidate, similarity(canonical, candidate))
            }
        }
        .sortedWith(
            compareByDescending<TitleEvidence> { it.similarity }
                .thenBy { normalize(it.canonical) }
                .thenBy { normalize(it.candidate) },
        )
        .first()
}

private data class MatchSignals(
    val title: TitleEvidence,
    val authorSimilarity: Double?,
    val authorConflict: Boolean,
    val contentTypeMatch: Boolean?,
    val contentTypeConflict: Boolean,
    val directEvidence: Boolean,
) {
    fun explanation(decision: ContentMatchDecision) = ContentMatchExplanation(
        directEvidence = directEvidence,
        title = ContentTitleEvidence(title.similarity, title.canonical, title.candidate),
        authorSimilarity = authorSimilarity,
        authorConflict = authorConflict,
        contentTypeMatch = contentTypeMatch,
        contentTypeConflict = contentTypeConflict,
        reasons = buildList {
            if (directEvidence) add("direct_mapping")
            if (contentTypeConflict) add("content_type_conflict")
            if (authorConflict) add("author_conflict")
            if (authorSimilarity == null) add("author_evidence_missing")
            add("decision:${decision.name.lowercase(Locale.ROOT)}")
        },
    )
}

private data class TitleEvidence(
    val canonical: String,
    val candidate: String,
    val similarity: Double,
)

private val nonWord = Regex("[^\\p{L}\\p{N}]+")
private val whitespace = Regex("\\s+")

private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)
    .replace(nonWord, " ")
    .trim()
    .replace(whitespace, " ")

private fun tokens(value: String): Set<String> = normalize(value)
    .split(' ')
    .filter(String::isNotBlank)
    .toSet()

private fun similarity(left: String, right: String): Double {
    val normalizedLeft = normalize(left)
    val normalizedRight = normalize(right)
    if (normalizedLeft.isNotEmpty() && normalizedLeft == normalizedRight) return 1.0
    return setSimilarity(tokens(left), tokens(right)) ?: 0.0
}

private fun Set<String>.normalizedValues(): Set<String> = map(::normalize)
    .filter(String::isNotBlank)
    .toSet()

private fun setSimilarity(left: Set<String>, right: Set<String>): Double? {
    if (left.isEmpty() || right.isEmpty()) return null
    return left.intersect(right).size.toDouble() / left.union(right).size.toDouble()
}
