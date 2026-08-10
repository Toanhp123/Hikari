package app.openstory.library.matching

data class ContentMatchPolicy(
    val version: Int = 1,
    val titleWeight: Double = 0.70,
    val authorWeight: Double = 0.20,
    val contentTypeWeight: Double = 0.10,
    val autoLinkAt: Double = 0.92,
    val reviewAt: Double = 0.72,
    val minimumAutoLinkTitleSimilarity: Double = 0.90,
    val minimumAutoLinkAuthorSimilarity: Double = 0.50,
) {
    init {
        require(version > 0) { "Policy version must be positive" }
        listOf(titleWeight, authorWeight, contentTypeWeight).forEach { weight ->
            require(weight in 0.0..1.0) { "Match weights must be between zero and one" }
        }
        require(titleWeight > 0.0) { "Title evidence must have positive weight" }
        require(autoLinkAt in 0.0..1.0) { "Auto-link threshold must be bounded" }
        require(reviewAt in 0.0..autoLinkAt) { "Review threshold must not exceed auto-link threshold" }
        require(minimumAutoLinkTitleSimilarity in 0.0..1.0)
        require(minimumAutoLinkAuthorSimilarity in 0.0..1.0)
    }
}
