package app.openstory.catalog.reconciliation

const val RECONCILIATION_POLICY_VERSION = 1

private const val DEFAULT_AUTO_TITLE_SIMILARITY = 0.92
private const val DEFAULT_REVIEW_TITLE_SIMILARITY = 0.75
private const val DEFAULT_AUTO_AUTHOR_SIMILARITY = 0.50
private const val DEFAULT_MINIMUM_WINNING_LEAD = 0.05

data class ReconciliationPolicy(
    val version: Int = RECONCILIATION_POLICY_VERSION,
    val autoTitleSimilarityAt: Double = DEFAULT_AUTO_TITLE_SIMILARITY,
    val reviewTitleSimilarityAt: Double = DEFAULT_REVIEW_TITLE_SIMILARITY,
    val autoAuthorSimilarityAt: Double = DEFAULT_AUTO_AUTHOR_SIMILARITY,
    val minimumWinningLead: Double = DEFAULT_MINIMUM_WINNING_LEAD,
) {
    init {
        require(version > 0)
        require(autoTitleSimilarityAt in 0.0..1.0)
        require(reviewTitleSimilarityAt in 0.0..autoTitleSimilarityAt)
        require(autoAuthorSimilarityAt in 0.0..1.0)
        require(minimumWinningLead in 0.0..1.0)
    }
}
