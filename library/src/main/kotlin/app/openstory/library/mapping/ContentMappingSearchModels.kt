package app.openstory.library.mapping

import app.openstory.common.id.PluginId
import app.openstory.library.matching.ContentMatchResult

enum class ContentMappingSearchStage {
    QUICK,
    DEFERRED,
    ALL,
    URL,
}

data class ContentMappingCandidate(
    val pluginId: PluginId,
    val pluginVersion: String,
    val sourceStoryId: String,
    val sourceUrl: String?,
    val title: String,
    val match: ContentMatchResult,
)

data class ContentMappingSearchFailure(
    val pluginId: PluginId?,
    val code: String,
    val retryable: Boolean,
)

data class ContentMappingSearchReport(
    val stage: ContentMappingSearchStage,
    val searchedPluginIds: List<PluginId>,
    val queryVariants: List<String>,
    val candidates: List<ContentMappingCandidate>,
    val failures: List<ContentMappingSearchFailure>,
)

data class ContentMappingSearchPolicy(
    val quickSourceCount: Int = 2,
    val maxQueryVariants: Int = 3,
    val maxCandidatesPerQuery: Int = 20,
    val maxCandidatesPerStage: Int = 50,
    val quickSourceTimeoutMillis: Long = 1_500L,
    val deferredSourceTimeoutMillis: Long = 8_000L,
) {
    init {
        require(quickSourceCount > 0) { "Quick source count must be positive" }
        require(maxQueryVariants in 1..MAX_QUERY_VARIANTS) { "Query variant cap is invalid" }
        require(maxCandidatesPerQuery in 1..MAX_CANDIDATES) { "Per-query candidate cap is invalid" }
        require(maxCandidatesPerStage in 1..MAX_CANDIDATES) { "Per-stage candidate cap is invalid" }
        require(quickSourceTimeoutMillis > 0L) { "Quick source timeout must be positive" }
        require(deferredSourceTimeoutMillis > 0L) { "Deferred source timeout must be positive" }
    }

    private companion object {
        const val MAX_QUERY_VARIANTS = 8
        const val MAX_CANDIDATES = 200
    }
}
