package app.openstory.matching

import app.openstory.common.StableId
import app.openstory.model.PluginId

data class CatalogSourceIdentity(
    val pluginId: PluginId,
    val sourceId: String,
) {
    init {
        StableId.requireValid(sourceId)
    }
}

data class TrustedCatalogMapping(
    val source: CatalogSourceIdentity,
    val target: CatalogSourceIdentity,
)

data class CatalogMatchPolicy(
    val autoLinkTitleSimilarityAt: Double,
    val reviewTitleSimilarityAt: Double,
    val autoLinkAuthorSimilarityAt: Double,
    val minimumAutoLinkLead: Double,
    val trustedDirectMappings: Set<TrustedCatalogMapping>,
) {
    init {
        require(autoLinkTitleSimilarityAt in 0.0..1.0)
        require(reviewTitleSimilarityAt in 0.0..autoLinkTitleSimilarityAt)
        require(autoLinkAuthorSimilarityAt in 0.0..1.0)
        require(minimumAutoLinkLead in 0.0..1.0)
        require(
            trustedDirectMappings.groupBy(TrustedCatalogMapping::source).values.all { mappings ->
                mappings.map(TrustedCatalogMapping::target).distinct().size == 1
            },
        ) {
            "Each trusted catalog source may map to only one target identity"
        }
    }
}

fun defaultCatalogMatchPolicy(
    trustedDirectMappings: Set<TrustedCatalogMapping> = emptySet(),
): CatalogMatchPolicy = CatalogMatchPolicy(
    autoLinkTitleSimilarityAt = DEFAULT_AUTO_LINK_TITLE_SIMILARITY,
    reviewTitleSimilarityAt = DEFAULT_REVIEW_TITLE_SIMILARITY,
    autoLinkAuthorSimilarityAt = DEFAULT_AUTO_LINK_AUTHOR_SIMILARITY,
    minimumAutoLinkLead = DEFAULT_MINIMUM_AUTO_LINK_LEAD,
    trustedDirectMappings = trustedDirectMappings,
)

private const val DEFAULT_AUTO_LINK_TITLE_SIMILARITY = 0.92
private const val DEFAULT_REVIEW_TITLE_SIMILARITY = 0.75
private const val DEFAULT_AUTO_LINK_AUTHOR_SIMILARITY = 0.50
private const val DEFAULT_MINIMUM_AUTO_LINK_LEAD = 0.05
