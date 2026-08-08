package app.openstory.matching

import app.openstory.model.StoryId

enum class MergeDecision {
    AUTO_LINK,
    REVIEW,
    SEPARATE,
}

data class CatalogMatchExplanation(
    val titleSimilarity: Double,
    val matchedTitle: String,
    val authorSimilarity: Double?,
    val authorConflict: Boolean,
    val contentTypeConflict: Boolean,
    val trustedDirectMapping: Boolean,
)

data class CatalogMatchResult(
    val storyId: StoryId,
    val score: Double,
    val decision: MergeDecision,
    val explanation: CatalogMatchExplanation,
)
