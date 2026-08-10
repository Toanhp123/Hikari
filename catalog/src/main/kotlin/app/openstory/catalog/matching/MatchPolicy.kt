package app.openstory.catalog.matching

data class MatchPolicy(
    val autoLinkTitleSimilarityAt: Double = 0.92,
    val reviewTitleSimilarityAt: Double = 0.75,
    val autoLinkAuthorSimilarityAt: Double = 0.50,
    val minimumAutoLinkLead: Double = 0.05,
) {
    init {
        require(autoLinkTitleSimilarityAt in 0.0..1.0)
        require(reviewTitleSimilarityAt in 0.0..autoLinkTitleSimilarityAt)
        require(autoLinkAuthorSimilarityAt in 0.0..1.0)
        require(minimumAutoLinkLead in 0.0..1.0)
    }
}
