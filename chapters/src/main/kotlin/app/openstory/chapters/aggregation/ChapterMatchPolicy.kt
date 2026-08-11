package app.openstory.chapters.aggregation

data class ChapterMatchPolicy(
    val version: Int = CURRENT_VERSION,
    val autoLinkThreshold: Double = 0.9,
    val reviewThreshold: Double = 0.65,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
