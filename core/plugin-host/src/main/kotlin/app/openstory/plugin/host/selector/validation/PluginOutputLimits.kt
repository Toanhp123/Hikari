package app.openstory.plugin.host.selector.validation

data class PluginOutputLimits(
    val maxOutputItems: Int = 10_000,
    val maxOutputSections: Int = 100,
    val maxOutputItemsPerSection: Int = 1_000,
    val maxTotalOutputItems: Int = 10_000,
    val maxReleaseItems: Int = 20_000,
    val maxTombstoneIds: Int = 20_000,
    val maxChapterBlocks: Int = 20_000,
    val maxChapterTextCharacters: Int = 5_000_000,
    val maxSpansPerBlock: Int = 2_000,
    val maxTotalSpans: Int = 100_000,
) {
    init {
        require(
            listOf(
                maxOutputItems,
                maxOutputSections,
                maxOutputItemsPerSection,
                maxTotalOutputItems,
                maxReleaseItems,
                maxTombstoneIds,
                maxChapterBlocks,
                maxChapterTextCharacters,
                maxSpansPerBlock,
                maxTotalSpans,
            ).all { it > 0 },
        ) {
            "Plugin output limits must be positive."
        }
    }
}
